package com.reservation.kafka.adapter

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.reservation.httpinterface.exceptions.ResponseBodyRequiredException
import com.reservation.httpinterface.timetable.FindTimeTableOccupancyHttpInterface
import com.reservation.httpinterface.timetable.response.FindTimeTableOccupancyInternallyHttpInterfaceResponse
import com.reservation.kafka.config.KafkaHeader.ERROR_REASON_KEY
import com.reservation.kafka.config.KafkaHeader.FAILED_TIMESTAMP_KEY
import com.reservation.kafka.config.KafkaHeader.ORIGINAL_TOPIC_KEY
import com.reservation.kafka.config.KafkaHeader.RETRY_COUNT_KEY
import com.reservation.kafka.event.TimeTableOccupancyReceivedEvent
import com.reservation.reservation.port.input.CreateReservationUseCase
import com.reservation.reservation.port.input.IsReservationExistsUseCase
import com.reservation.reservation.port.input.command.CreateReservationCommand
import com.reservation.reservation.port.input.query.IsReservationExistsQuery
import com.reservation.utilities.logger.loggerFactory
import io.confluent.parallelconsumer.ParallelStreamProcessor
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlin.math.pow
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.Headers
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

@Component
@SuppressWarnings("TooManyFunctions")
class TimeTableOccupancyKafkaListener(
    private val httpInterface: FindTimeTableOccupancyHttpInterface,
    private val createReservationUseCase: CreateReservationUseCase,
    private val isReservationExistsUseCase: IsReservationExistsUseCase,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val parallelEventConsumer: ParallelStreamProcessor<String, String>,
    private val objectMapper: ObjectMapper,
) {
    private val log = loggerFactory<TimeTableOccupancyKafkaListener>()

    companion object {
        const val TOPIC = "time-table-occupancy"
        const val DLT_SUFFIX = "dlt"
        const val GROUP_ID = "reservation-service"
        const val RETRY_ATTEMPTS = 3
        const val BACK_OFF_DELAY = 1000
        const val BACK_OFF_MULTIPLIER = 2.0
        const val WAIT_FOR_GET_TIME = 5L
        const val DRAIN_DURATION = 30L
        const val ADD_RETRY_COUNT = 1
    }

    @PostConstruct
    fun init() {
        val topics = mutableListOf<String>()
        repeat(RETRY_ATTEMPTS) {
            when (it) {
                0 -> topics.add(TOPIC)
                else -> topics.add("$TOPIC-RETRY-$it")
            }
        }

        parallelEventConsumer.subscribe(topics)
        parallelEventConsumer.poll { context ->
            val record = context.singleRecord
            val key = record.key()
            val payloadString = record.value()
            val headers = record.headers()

            val retryCount = retryCount(headers)
            if (retryCount + ADD_RETRY_COUNT >= RETRY_ATTEMPTS) {
                onHandleDlt(
                    originalTopic = TOPIC,
                    partitionKey = key,
                    headers = headers,
                    error = "Retry-count exceeded ($retryCount).",
                    event = payloadString,
                )
                return@poll
            }

            val payload: TimeTableOccupancyReceivedEvent? = parseJson(key, headers, payloadString)
            if (payload == null) return@poll

            runCatching { onEventHandler(payload) }
                .onFailure { error ->
                    log.error("Error in TimeTableOccupancyEvent:", error)
                    try {
                        val backOff =
                            BACK_OFF_DELAY * BACK_OFF_MULTIPLIER.pow((retryCount + ADD_RETRY_COUNT))
                        Thread.sleep(backOff.toLong())
                        retry(headers, kafkaKey = key, payloadString)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
        }
    }

    @PreDestroy
    fun destroy() {
        parallelEventConsumer.closeDrainFirst(Duration.ofSeconds(DRAIN_DURATION))
    }

    private fun isExists(
        timeTableId: String,
        timeTableOccupancyId: String,
    ) = isReservationExistsUseCase.execute(
        IsReservationExistsQuery(
            timeTableId = timeTableId,
            timeTableOccupancyId = timeTableOccupancyId,
        ),
    )

    private fun createReservationCommand(
        httpInterfaceResponse: FindTimeTableOccupancyInternallyHttpInterfaceResponse,
    ): CreateReservationCommand {
        val table = httpInterfaceResponse.table
        val book = httpInterfaceResponse.book

        return CreateReservationCommand(
            restaurantId = table.restaurantId,
            userId = book.userId,
            timeTableId = table.timeTableId,
            timeTableOccupancyId = book.timeTableOccupancyId,
            date = table.date,
            day = table.day,
            startTime = table.startTime,
            endTime = table.endTime,
            tableNumber = table.tableNumber,
            tableSize = table.tableSize,
            occupiedDatetime = book.occupiedDatetime,
        )
    }

    private fun parseJson(
        key: String,
        header: Headers,
        payloadString: String,
    ): TimeTableOccupancyReceivedEvent? =
        try {
            objectMapper.readValue(
                payloadString,
                TimeTableOccupancyReceivedEvent::class.java,
            )
        } catch (e: JsonProcessingException) {
            log.error("INVALID JSON", e)
            onHandleDlt(TOPIC, key, header, e.toString(), payloadString)
            null
        }

    private fun originalTopic(header: Headers) =
        header.lastHeader(ORIGINAL_TOPIC_KEY)?.let { String(it.value(), StandardCharsets.UTF_8) }
            ?: TOPIC

    private fun retryCount(header: Headers) =
        header.lastHeader(RETRY_COUNT_KEY)
            ?.let { String(it.value(), StandardCharsets.UTF_8).toIntOrNull() }
            ?: 0

    private fun retry(
        header: Headers,
        kafkaKey: String,
        payloadString: String,
    ) {
        val originalTopic = originalTopic(header)
        val retryCount = retryCount(header) + ADD_RETRY_COUNT
        val topic = "$originalTopic-RETRY-$retryCount"

        val record =
            ProducerRecord(
                topic,
                kafkaKey,
                payloadString,
            )

        record.headers()
            .add(RETRY_COUNT_KEY, retryCount.toString().toByteArray(StandardCharsets.UTF_8))
        record.headers().add(ORIGINAL_TOPIC_KEY, originalTopic.toByteArray(StandardCharsets.UTF_8))

        kafkaTemplate.send(record).thenAccept { log.info("Result: $it") }
    }

    private fun createReservation(body: FindTimeTableOccupancyInternallyHttpInterfaceResponse?) {
        if (body == null) {
            throw ResponseBodyRequiredException()
        }

        val command = createReservationCommand(body)
        createReservationUseCase.execute(command)
    }

    fun onEventHandler(event: TimeTableOccupancyReceivedEvent) {
        val timeTableId = event.timeTableId
        val timeTableOccupancyId = event.timeTableOccupancyId

        if (isExists(timeTableId, timeTableOccupancyId)) {
            return
        }

        val responseEntity =
            httpInterface.findTimeTableOccupancyInternally(
                timeTableId = timeTableId,
                timeTableOccupancyId = timeTableOccupancyId,
            )

        createReservation(responseEntity.body)
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
            ProducerRecord(
                "$originalTopic-$DLT_SUFFIX", // topic
                partitionKey, // key (positional)
                event, // value (positional)
            ).apply {
                headers().add(ORIGINAL_TOPIC_KEY, headers.lastHeader(ORIGINAL_TOPIC_KEY).value())
                headers().add(ERROR_REASON_KEY, error.toByteArray())
                headers().add(FAILED_TIMESTAMP_KEY, Instant.now().toString().toByteArray())
            }

        runCatching {
            kafkaTemplate.send(dltRecord).get(WAIT_FOR_GET_TIME, TimeUnit.SECONDS)
            log.error("Sent to DLT: topic=${dltRecord.topic()}, key=$partitionKey")
        }
            .onFailure { e -> log.error("CRITICAL: Failed to send to DLT", e) }
    }
}
