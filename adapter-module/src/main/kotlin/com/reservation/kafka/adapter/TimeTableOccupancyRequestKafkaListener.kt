package com.reservation.kafka.adapter

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.reservation.kafka.config.KafkaConfig.Companion.OCCUPANCY_REQUEST_CONSUMER
import com.reservation.kafka.config.KafkaHeader.ERROR_REASON_KEY
import com.reservation.kafka.config.KafkaHeader.FAILED_TIMESTAMP_KEY
import com.reservation.kafka.config.KafkaHeader.ORIGINAL_TOPIC_KEY
import com.reservation.kafka.config.KafkaHeader.RETRY_COUNT_KEY
import com.reservation.kafka.config.KafkaTopic
import com.reservation.kafka.event.TimeTableOccupancyRequestedEvent
import com.reservation.timetable.port.input.AbandonTimeTableOccupancyUseCase
import com.reservation.timetable.port.input.OccupyTimeTableUseCase
import com.reservation.timetable.port.input.command.request.OccupyTimeTableCommand
import com.reservation.utilities.logger.loggerFactory
import io.confluent.parallelconsumer.ParallelStreamProcessor
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlin.math.pow
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.Headers
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * 좌석을 확보한 요청을 받아 실제로 저장한다.
 *
 * ## 이 리스너가 순서에 기대는 부분
 *
 * 파티션 키가 슬롯이고 컨슈머가 `ProcessingOrder.KEY`로 돌기 때문에, **같은 슬롯의 메시지는
 * 동시에 두 개가 처리되지 않는다.** 앞 메시지의 DB 커밋이 끝나야 다음 메시지가 시작된다.
 * 그래서 [OccupyTimeTableUseCase]가 소비 시점에 "지금 비어 있는 아무 좌석 행 하나"를 그냥
 * 집어도 두 요청이 같은 행을 두고 다투지 않는다. 이 성질이 없으면 병렬 워커 둘이 같은 행을
 * 골라 하나가 다른 하나를 덮어쓴다.
 *
 * 다만 이 보장은 **한 프로세서 인스턴스 안에서** 완전하다. 앱을 여러 대 띄우고 재시도 토픽의
 * 파티션이 다른 인스턴스로 배정되면, 원본 토픽의 요청과 재시도 요청이 같은 슬롯에 대해 겹칠
 * 수 있다. 그 창을 닫는 것이 Phase 4의 DB row lock이다 — 순서 보장은 경합을 없애는 장치이지
 * 마지막 방어선이 아니다.
 *
 * ## 실패 처리
 *
 * 중간 실패에서는 좌석을 되돌리지 않는다. 재시도가 남아 있는데 반납하면 그 자리를 다른
 * 사용자가 가져가고, 뒤이은 재시도까지 성공하면서 좌석 수보다 많은 예약이 저장된다.
 * 회수는 재시도를 모두 소진해 DLT로 보내는 시점에 한 번만 한다.
 */
@Component
@Suppress("TooManyFunctions")
class TimeTableOccupancyRequestKafkaListener(
    private val occupyTimeTableUseCase: OccupyTimeTableUseCase,
    private val abandonTimeTableOccupancyUseCase: AbandonTimeTableOccupancyUseCase,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Qualifier(OCCUPANCY_REQUEST_CONSUMER)
    private val parallelEventConsumer: ParallelStreamProcessor<String, String>,
    private val objectMapper: ObjectMapper,
) {
    private val log = loggerFactory<TimeTableOccupancyRequestKafkaListener>()

    companion object {
        const val TOPIC = KafkaTopic.TIMETABLE_OCCUPANCY_REQUESTED
        const val DLT_SUFFIX = "dlt"
        const val RETRY_ATTEMPTS = 3
        const val BACK_OFF_DELAY = 1000
        const val BACK_OFF_MULTIPLIER = 2.0
        const val WAIT_FOR_GET_TIME = 5L
        const val DRAIN_DURATION = 30L
        const val ADD_RETRY_COUNT = 1
    }

    @PostConstruct
    fun init() {
        parallelEventConsumer.subscribe(subscribedTopics())
        parallelEventConsumer.poll { context ->
            val record = context.singleRecord
            handle(
                key = record.key(),
                headers = record.headers(),
                payloadString = record.value(),
            )
        }
    }

    @PreDestroy
    fun destroy() {
        parallelEventConsumer.closeDrainFirst(Duration.ofSeconds(DRAIN_DURATION))
    }

    private fun subscribedTopics(): List<String> =
        (0 until RETRY_ATTEMPTS).map {
            if (it == 0) TOPIC else "$TOPIC-RETRY-$it"
        }

    /**
     * 재시도 횟수를 확인하기 **전에** 파싱한다.
     *
     * 기존 리스너는 순서가 반대인데, 여기서는 포기하는 시점에 좌석을 되돌려야 하고 그러려면
     * 페이로드에서 슬롯과 사용자를 알아내야 한다. 파싱조차 안 되면 되돌릴 대상을 특정할 수
     * 없어 그 한 자리는 카운터 TTL이 끝날 때까지 묶인다 — 그래서 별도로 크게 남긴다.
     */
    fun handle(
        key: String,
        headers: Headers,
        payloadString: String,
    ) {
        val payload = parseJson(key, headers, payloadString)
        if (payload == null) {
            log.error("Seat cannot be released for an unparsable request. key={}", key)
            return
        }

        val retryCount = retryCount(headers)
        if (retryCount + ADD_RETRY_COUNT >= RETRY_ATTEMPTS) {
            giveUp(key, headers, payload, payloadString, "Retry-count exceeded ($retryCount).")
            return
        }

        runCatching { occupyTimeTableUseCase.execute(payload.toCommand()) }
            .onFailure { error ->
                log.error("Error in TimeTableOccupancyRequestedEvent:", error)
                backOffThenRetry(retryCount, headers, key, payloadString)
            }
    }

    private fun backOffThenRetry(
        retryCount: Int,
        headers: Headers,
        key: String,
        payloadString: String,
    ) {
        try {
            val backOff = BACK_OFF_DELAY * BACK_OFF_MULTIPLIER.pow(retryCount + ADD_RETRY_COUNT)
            Thread.sleep(backOff.toLong())
            retry(headers, key, payloadString)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            log.error("Interrupted before retrying. key={}", key, exception)
        }
    }

    /** 더 이상 저장을 시도하지 않는다. DLT로 옮기고 잡아 둔 좌석을 회수한다. */
    private fun giveUp(
        key: String,
        headers: Headers,
        payload: TimeTableOccupancyRequestedEvent,
        payloadString: String,
        error: String,
    ) {
        onHandleDlt(TOPIC, key, headers, error, payloadString)
        abandonTimeTableOccupancyUseCase.execute(payload.toCommand())
    }

    private fun TimeTableOccupancyRequestedEvent.toCommand() =
        OccupyTimeTableCommand(
            userId = userId,
            restaurantId = restaurantId,
            date = date,
            startTime = startTime,
        )

    private fun parseJson(
        key: String,
        headers: Headers,
        payloadString: String,
    ): TimeTableOccupancyRequestedEvent? =
        try {
            objectMapper.readValue(payloadString, TimeTableOccupancyRequestedEvent::class.java)
        } catch (exception: JsonProcessingException) {
            log.error("INVALID JSON", exception)
            onHandleDlt(TOPIC, key, headers, exception.toString(), payloadString)
            null
        }

    private fun originalTopic(headers: Headers) =
        headers.lastHeader(ORIGINAL_TOPIC_KEY)?.let { String(it.value(), StandardCharsets.UTF_8) }
            ?: TOPIC

    private fun retryCount(headers: Headers) =
        headers.lastHeader(RETRY_COUNT_KEY)
            ?.let { String(it.value(), StandardCharsets.UTF_8).toIntOrNull() }
            ?: 0

    private fun retry(
        headers: Headers,
        kafkaKey: String,
        payloadString: String,
    ) {
        val originalTopic = originalTopic(headers)
        val retryCount = retryCount(headers) + ADD_RETRY_COUNT
        val record = ProducerRecord("$originalTopic-RETRY-$retryCount", kafkaKey, payloadString)

        record.headers()
            .add(RETRY_COUNT_KEY, retryCount.toString().toByteArray(StandardCharsets.UTF_8))
        record.headers()
            .add(ORIGINAL_TOPIC_KEY, originalTopic.toByteArray(StandardCharsets.UTF_8))

        kafkaTemplate.send(record).thenAccept { log.info("Retry scheduled: $it") }
    }

    fun onHandleDlt(
        originalTopic: String,
        partitionKey: String,
        headers: Headers,
        error: String,
        event: String,
    ) {
        log.error(
            """
            original topic: {},
            error: {}
            event: {}
            """.trimIndent(),
            originalTopic,
            error,
            event,
        )

        val dltRecord =
            ProducerRecord("$originalTopic-$DLT_SUFFIX", partitionKey, event).apply {
                headers().add(
                    ORIGINAL_TOPIC_KEY,
                    originalTopic.toByteArray(StandardCharsets.UTF_8),
                )
                headers().add(ERROR_REASON_KEY, error.toByteArray(StandardCharsets.UTF_8))
                headers().add(
                    FAILED_TIMESTAMP_KEY,
                    Instant.now().toString().toByteArray(StandardCharsets.UTF_8),
                )
            }

        runCatching {
            kafkaTemplate.send(dltRecord).get(WAIT_FOR_GET_TIME, TimeUnit.SECONDS)
            log.error("Sent to DLT: topic=${dltRecord.topic()}, key=$partitionKey")
        }
            .onFailure { exception -> log.error("CRITICAL: Failed to send to DLT", exception) }
    }
}
