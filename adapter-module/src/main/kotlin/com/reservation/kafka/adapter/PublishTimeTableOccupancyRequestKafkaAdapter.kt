package com.reservation.kafka.adapter

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.reservation.kafka.config.KafkaHeader.ORIGINAL_TOPIC_KEY
import com.reservation.kafka.config.KafkaHeader.RETRY_COUNT_KEY
import com.reservation.kafka.config.KafkaTopic
import com.reservation.kafka.event.TimeTableOccupancyRequestedEvent
import com.reservation.kafka.util.TimeTableSlotKeyGenerator
import com.reservation.timetable.port.output.PublishTimeTableOccupancyRequest
import com.reservation.timetable.port.output.PublishTimeTableOccupancyRequest.TimeTableOccupancyRequest
import com.reservation.utilities.logger.loggerFactory
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.KafkaException
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.TimeoutException

/**
 * 좌석을 확보한 요청을 슬롯 키로 발행한다.
 *
 * ## 왜 `get()`으로 기다리는가
 *
 * 발행을 fire-and-forget으로 두면 응답은 몇 밀리초 빨라지지만, 브로커가 받지 못한 요청이
 * 사용자에게는 성공으로 보인다. 그 요청은 좌석 하나를 이미 소비했으므로 결과는 "예약됐다고
 * 들었는데 아무 데도 없고, 그 자리는 아무도 못 쓴다"가 된다. 조용히 사라지는 예약은 느린
 * 예약보다 나쁘다.
 *
 * 기다리는 비용도 걷어낸 것에 비하면 작다. `linger.ms=5`, `acks=1` 기준 한 자릿수 밀리초이고,
 * 그 자리를 대신하기 전에 있던 것은 최대 2분을 기다리던 공정 분산락이었다.
 */
@Component
class PublishTimeTableOccupancyRequestKafkaAdapter(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) : PublishTimeTableOccupancyRequest {
    private val log = loggerFactory<PublishTimeTableOccupancyRequestKafkaAdapter>()

    companion object {
        const val PUBLISH_TIME_OUT = 10L
        private const val INITIAL_RETRY_COUNT = "0"
    }

    override fun publish(request: TimeTableOccupancyRequest): Boolean {
        val key =
            TimeTableSlotKeyGenerator.slot(
                request.restaurantId,
                request.date,
                request.startTime,
            )

        return try {
            kafkaTemplate.send(request.toRecord(key)).get(PUBLISH_TIME_OUT, SECONDS)
            true
        } catch (exception: InterruptedException) {
            // 인터럽트 플래그를 복원하지 않으면 상위(요청 스레드)가 중단 사실을 알 길이 없다.
            Thread.currentThread().interrupt()
            log.error("Interrupted while publishing an occupancy request: {}", key, exception)
            false
        } catch (exception: ExecutionException) {
            log.error("Broker rejected the occupancy request: {}", key, exception)
            false
        } catch (exception: TimeoutException) {
            log.error("Timed out publishing the occupancy request: {}", key, exception)
            false
        } catch (exception: KafkaException) {
            log.error("Failed to publish the occupancy request: {}", key, exception)
            false
        } catch (exception: JsonProcessingException) {
            log.error("Failed to serialize the occupancy request: {}", key, exception)
            false
        }
    }

    private fun TimeTableOccupancyRequest.toRecord(key: String): ProducerRecord<String, String> {
        val payload =
            objectMapper.writeValueAsString(
                TimeTableOccupancyRequestedEvent(
                    restaurantId = restaurantId,
                    date = date,
                    startTime = startTime,
                    userId = userId,
                ),
            )

        return ProducerRecord(KafkaTopic.TIMETABLE_OCCUPANCY_REQUESTED, key, payload).apply {
            headers().add(
                RETRY_COUNT_KEY,
                INITIAL_RETRY_COUNT.toByteArray(StandardCharsets.UTF_8),
            )
            headers().add(
                ORIGINAL_TOPIC_KEY,
                KafkaTopic.TIMETABLE_OCCUPANCY_REQUESTED.toByteArray(StandardCharsets.UTF_8),
            )
        }
    }
}
