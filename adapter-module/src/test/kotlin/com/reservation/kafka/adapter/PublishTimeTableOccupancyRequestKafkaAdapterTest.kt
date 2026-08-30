package com.reservation.kafka.adapter

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.reservation.kafka.config.KafkaHeader.ORIGINAL_TOPIC_KEY
import com.reservation.kafka.config.KafkaHeader.RETRY_COUNT_KEY
import com.reservation.kafka.config.KafkaTopic
import com.reservation.kafka.event.TimeTableOccupancyRequestedEvent
import com.reservation.timetable.port.output.PublishTimeTableOccupancyRequest.TimeTableOccupancyRequest
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.KafkaException
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.CompletableFuture

/**
 * 발행 어댑터가 지키는 두 가지.
 *
 * 1. **파티션 키가 슬롯이어야 한다.** 이 키가 틀리면 같은 슬롯의 요청이 서로 다른 파티션으로
 *    흩어지고, 순서 보장이 통째로 무의미해진다. Phase 3의 전제가 그대로 무너지는 지점이다.
 * 2. **실패를 삼키지 않는다.** 발행에 실패했는데 true를 돌려주면 좌석 하나를 소비한 요청이
 *    아무 데도 저장되지 않은 채 사용자에게는 성공으로 보인다.
 */
class PublishTimeTableOccupancyRequestKafkaAdapterTest : FunSpec(
    {
        val kafkaTemplate = mockk<KafkaTemplate<String, String>>()
        val objectMapper = ObjectMapper().registerKotlinModule().findAndRegisterModules()
        val adapter = PublishTimeTableOccupancyRequestKafkaAdapter(kafkaTemplate, objectMapper)

        val request =
            TimeTableOccupancyRequest(
                restaurantId = UuidGenerator.generate(),
                date = LocalDate.of(2026, 8, 26),
                startTime = LocalTime.of(11, 0),
                userId = UuidGenerator.generate(),
            )

        beforeTest { clearAllMocks() }

        test("슬롯을 파티션 키로 삼아 발행한다.") {
            val record = slot<ProducerRecord<String, String>>()
            every { kafkaTemplate.send(capture(record)) } returns
                CompletableFuture.completedFuture(mockk<SendResult<String, String>>())

            adapter.publish(request) shouldBe true

            record.captured.topic() shouldBe KafkaTopic.TIMETABLE_OCCUPANCY_REQUESTED
            // userId가 섞이면 같은 슬롯의 요청이 파티션마다 흩어져 순서 보장이 사라진다.
            record.captured.key() shouldBe "${request.restaurantId}:20260826:1100"
        }

        test("페이로드에 슬롯과 사용자만 담고 특정 좌석은 담지 않는다.") {
            val record = slot<ProducerRecord<String, String>>()
            every { kafkaTemplate.send(capture(record)) } returns
                CompletableFuture.completedFuture(mockk<SendResult<String, String>>())

            adapter.publish(request)

            val payload =
                objectMapper.readValue(
                    record.captured.value(),
                    TimeTableOccupancyRequestedEvent::class.java,
                )

            payload.restaurantId shouldBe request.restaurantId
            payload.date shouldBe request.date
            payload.startTime shouldBe request.startTime
            payload.userId shouldBe request.userId
        }

        test("재시도 헤더를 0으로 심어 보낸다.") {
            val record = slot<ProducerRecord<String, String>>()
            every { kafkaTemplate.send(capture(record)) } returns
                CompletableFuture.completedFuture(mockk<SendResult<String, String>>())

            adapter.publish(request)

            val headers = record.captured.headers()
            String(headers.lastHeader(RETRY_COUNT_KEY).value(), StandardCharsets.UTF_8) shouldBe "0"
            String(
                headers.lastHeader(ORIGINAL_TOPIC_KEY).value(),
                StandardCharsets.UTF_8,
            ) shouldBe KafkaTopic.TIMETABLE_OCCUPANCY_REQUESTED
        }

        test("브로커가 거절하면 false를 돌려준다.") {
            every { kafkaTemplate.send(any<ProducerRecord<String, String>>()) } returns
                CompletableFuture.failedFuture(KafkaException("broker is down"))

            // 여기서 true를 돌려주면 좌석만 소비하고 사라지는 예약이 만들어진다.
            adapter.publish(request) shouldBe false
        }

        test("전송 자체가 예외로 끝나도 false를 돌려준다.") {
            every {
                kafkaTemplate.send(any<ProducerRecord<String, String>>())
            } throws KafkaException("producer is closed")

            adapter.publish(request) shouldBe false
        }
    },
)
