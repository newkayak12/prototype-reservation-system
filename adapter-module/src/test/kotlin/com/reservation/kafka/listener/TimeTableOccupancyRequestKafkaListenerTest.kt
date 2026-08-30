package com.reservation.kafka.listener

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.reservation.kafka.adapter.TimeTableOccupancyRequestKafkaListener
import com.reservation.kafka.adapter.TimeTableOccupancyRequestKafkaListener.Companion.RETRY_ATTEMPTS
import com.reservation.kafka.adapter.TimeTableOccupancyRequestKafkaListener.Companion.TOPIC
import com.reservation.kafka.config.KafkaHeader.ORIGINAL_TOPIC_KEY
import com.reservation.kafka.config.KafkaHeader.RETRY_COUNT_KEY
import com.reservation.kafka.event.TimeTableOccupancyRequestedEvent
import com.reservation.timetable.port.input.AbandonTimeTableOccupancyUseCase
import com.reservation.timetable.port.input.OccupyTimeTableUseCase
import com.reservation.timetable.port.input.command.request.OccupyTimeTableCommand
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.confluent.parallelconsumer.ParallelStreamProcessor
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.CompletableFuture

/**
 * 실패 처리 규칙을 못 박아 두는 테스트.
 *
 * 이 리스너에서 가장 틀리기 쉬운 것은 "실패했으니 좌석을 돌려주자"는 반사적인 판단이다.
 * 재시도가 남아 있는데 되돌리면 그 자리를 다른 사용자가 가져가고, 뒤이은 재시도까지 성공하면서
 * **좌석 수보다 많은 예약이 저장된다.** 되돌리기가 오버부킹을 만드는 셈이라, 회수는 재시도를
 * 모두 소진한 시점에 정확히 한 번만 일어나야 한다.
 */
class TimeTableOccupancyRequestKafkaListenerTest : FunSpec(
    {
        val occupyTimeTableUseCase = mockk<OccupyTimeTableUseCase>()
        val abandonTimeTableOccupancyUseCase =
            mockk<AbandonTimeTableOccupancyUseCase>(relaxed = true)
        val kafkaTemplate = mockk<KafkaTemplate<String, String>>(relaxed = true)
        val parallelEventConsumer = mockk<ParallelStreamProcessor<String, String>>(relaxed = true)
        val objectMapper = ObjectMapper().registerKotlinModule().findAndRegisterModules()

        val listener =
            TimeTableOccupancyRequestKafkaListener(
                occupyTimeTableUseCase = occupyTimeTableUseCase,
                abandonTimeTableOccupancyUseCase = abandonTimeTableOccupancyUseCase,
                kafkaTemplate = kafkaTemplate,
                parallelEventConsumer = parallelEventConsumer,
                objectMapper = objectMapper,
            )

        val restaurantId = UuidGenerator.generate()
        val userId = UuidGenerator.generate()
        val event =
            TimeTableOccupancyRequestedEvent(
                restaurantId = restaurantId,
                date = LocalDate.of(2026, 8, 26),
                startTime = LocalTime.of(11, 0),
                userId = userId,
            )
        val key = "$restaurantId:20260826:1100"

        fun payload() = objectMapper.writeValueAsString(event)

        fun headersWithRetryCount(count: Int) =
            RecordHeaders().apply {
                add(RETRY_COUNT_KEY, count.toString().toByteArray(StandardCharsets.UTF_8))
            }

        beforeTest {
            clearAllMocks()
            every {
                kafkaTemplate.send(any<ProducerRecord<String, String>>())
            } returns CompletableFuture.completedFuture(mockk(relaxed = true))
        }

        test("정상 요청은 페이로드의 슬롯과 사용자로 저장을 호출한다.") {
            val command = slot<OccupyTimeTableCommand>()
            every { occupyTimeTableUseCase.execute(capture(command)) } returns true

            listener.handle(key, headersWithRetryCount(0), payload())

            command.captured.restaurantId shouldBe restaurantId
            command.captured.userId shouldBe userId
            command.captured.date shouldBe event.date
            command.captured.startTime shouldBe event.startTime

            verify(exactly = 0) { abandonTimeTableOccupancyUseCase.execute(any()) }
        }

        test("저장에 실패하면 재시도 토픽으로 넘기고 좌석은 되돌리지 않는다.") {
            val record = slot<ProducerRecord<String, String>>()
            every {
                occupyTimeTableUseCase.execute(any())
            } throws DataIntegrityViolationException("deadlock")
            every { kafkaTemplate.send(capture(record)) } returns
                CompletableFuture.completedFuture(mockk(relaxed = true))

            listener.handle(key, headersWithRetryCount(0), payload())

            record.captured.topic() shouldBe "$TOPIC-RETRY-1"
            // 파티션 키는 재시도에서도 유지돼야 같은 슬롯의 순서가 유지된다.
            record.captured.key() shouldBe key

            // 아직 기회가 남았다. 여기서 반납하면 그 자리를 남이 가져간 뒤 재시도가
            // 성공하면서 좌석 수를 넘긴다.
            verify(exactly = 0) { abandonTimeTableOccupancyUseCase.execute(any()) }
        }

        test("재시도를 모두 소진하면 DLT로 보내고 좌석을 한 번 되돌린다.") {
            val abandoned = slot<OccupyTimeTableCommand>()
            val record = slot<ProducerRecord<String, String>>()
            every { abandonTimeTableOccupancyUseCase.execute(capture(abandoned)) } returns Unit
            every { kafkaTemplate.send(capture(record)) } returns
                CompletableFuture.completedFuture(mockk(relaxed = true))

            listener.handle(key, headersWithRetryCount(RETRY_ATTEMPTS - 1), payload())

            record.captured.topic() shouldBe "$TOPIC-dlt"
            abandoned.captured.restaurantId shouldBe restaurantId
            abandoned.captured.userId shouldBe userId

            // 소진 시점에는 저장을 다시 시도하지 않는다.
            verify(exactly = 0) { occupyTimeTableUseCase.execute(any()) }
            verify(exactly = 1) { abandonTimeTableOccupancyUseCase.execute(any()) }
        }

        test("페이로드를 읽지 못하면 DLT로만 보내고 좌석은 손대지 못한다.") {
            listener.handle(key, headersWithRetryCount(0), "{ this is not json")

            // 슬롯과 사용자를 알 수 없으니 되돌릴 대상을 특정할 수 없다. 그 한 자리는
            // 카운터 TTL이 끝날 때까지 묶인다 — 어댑터가 로그로 크게 남기는 이유다.
            verify(exactly = 0) {
                occupyTimeTableUseCase.execute(any())
                abandonTimeTableOccupancyUseCase.execute(any())
            }
            verify(exactly = 1) { kafkaTemplate.send(any<ProducerRecord<String, String>>()) }
        }

        test("인터럽트된 상태에서 저장에 실패하면 재시도를 보내지 않고 인터럽트 플래그만 복원한다.") {
            every {
                occupyTimeTableUseCase.execute(any())
            } throws DataIntegrityViolationException("deadlock")

            // Thread.sleep이 즉시 InterruptedException을 던지도록 handle 호출 직전에 인터럽트한다.
            Thread.currentThread().interrupt()
            try {
                listener.handle(key, headersWithRetryCount(0), payload())

                // sleep이 즉시 실패하므로 retry()까지 도달하지 못한다 — 재시도 레코드 미발행.
                verify(exactly = 0) { kafkaTemplate.send(any<ProducerRecord<String, String>>()) }
                // 재시도 소진 판정이 아니므로 좌석 회수도 일어나지 않는다.
                verify(exactly = 0) { abandonTimeTableOccupancyUseCase.execute(any()) }
            } finally {
                // SUT가 catch에서 인터럽트 플래그를 복원하므로 다음 테스트를 위해 걷어낸다.
                Thread.interrupted()
            }
        }

        test("DLT 전송이 실패해도 좌석 회수는 그대로 진행된다.") {
            val abandoned = slot<OccupyTimeTableCommand>()
            every {
                abandonTimeTableOccupancyUseCase.execute(capture(abandoned))
            } returns Unit
            every {
                kafkaTemplate.send(any<ProducerRecord<String, String>>())
            } returns
                CompletableFuture<SendResult<String, String>>().apply {
                    completeExceptionally(RuntimeException("broker unavailable"))
                }

            // giveUp 경로: DLT 전송 자체가 실패해도 handle() 밖으로 예외가 새어나가면 안 된다.
            listener.handle(key, headersWithRetryCount(RETRY_ATTEMPTS - 1), payload())

            abandoned.captured.restaurantId shouldBe restaurantId
            abandoned.captured.userId shouldBe userId
            // DLT 실패가 좌석을 묶어두면 안 되므로 회수는 정확히 한 번 일어나야 한다.
            verify(exactly = 1) { abandonTimeTableOccupancyUseCase.execute(any()) }
        }

        test(
            "originalTopic 헤더가 있으면 그 값을 재시도 토픽 접두로 쓰고, " +
                "retryCount 헤더가 없으면 0으로 폴백한다.",
        ) {
            val headerTopic = "custom-origin-topic"
            val headers =
                RecordHeaders().apply {
                    add(ORIGINAL_TOPIC_KEY, headerTopic.toByteArray(StandardCharsets.UTF_8))
                }
            val record = slot<ProducerRecord<String, String>>()
            every {
                occupyTimeTableUseCase.execute(any())
            } throws DataIntegrityViolationException("deadlock")
            every { kafkaTemplate.send(capture(record)) } returns
                CompletableFuture.completedFuture(mockk(relaxed = true))

            listener.handle(key, headers, payload())

            // 접두는 TOPIC 상수가 아니라 헤더에서 온 값이고, 접미는 부재 헤더가 0으로
            // 폴백했음을 함께 증명한다.
            record.captured.topic() shouldBe "$headerTopic-RETRY-1"
        }

        test("retryCount 헤더 값이 숫자가 아니면 0으로 취급해 재시도 카운트를 계산한다.") {
            val headers =
                RecordHeaders().apply {
                    add(RETRY_COUNT_KEY, "abc".toByteArray(StandardCharsets.UTF_8))
                }
            val record = slot<ProducerRecord<String, String>>()
            every {
                occupyTimeTableUseCase.execute(any())
            } throws DataIntegrityViolationException("deadlock")
            every { kafkaTemplate.send(capture(record)) } returns
                CompletableFuture.completedFuture(mockk(relaxed = true))

            listener.handle(key, headers, payload())

            // 0으로 취급되지 않았다면 접미와 헤더 값이 "2" 등으로 어긋났을 것이다.
            record.captured.topic() shouldBe "$TOPIC-RETRY-1"
            val retryCountHeader = record.captured.headers().lastHeader(RETRY_COUNT_KEY)
            String(retryCountHeader!!.value(), StandardCharsets.UTF_8) shouldBe "1"
        }
    },
)
