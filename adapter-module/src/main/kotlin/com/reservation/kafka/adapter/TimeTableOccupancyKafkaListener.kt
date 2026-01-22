package com.reservation.kafka.adapter

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.reservation.httpinterface.exceptions.ResponseBodyRequiredException
import com.reservation.httpinterface.timetable.FindTimeTableOccupancyHttpInterface
import com.reservation.httpinterface.timetable.response.FindTimeTableOccupancyInternallyHttpInterfaceResponse
import com.reservation.kafka.event.TimeTableOccupancyReceivedEvent
import com.reservation.reservation.port.input.CreateReservationUseCase
import com.reservation.reservation.port.input.IsReservationExistsUseCase
import com.reservation.reservation.port.input.command.CreateReservationCommand
import com.reservation.reservation.port.input.query.IsReservationExistsQuery
import com.reservation.utilities.logger.loggerFactory
import io.confluent.parallelconsumer.ParallelStreamProcessor
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.TimeUnit

@Component
class TimeTableOccupancyKafkaListener(
    private val httpInterface: FindTimeTableOccupancyHttpInterface,
    private val createReservationUseCase: CreateReservationUseCase,
    private val isReservationExistsUseCase: IsReservationExistsUseCase,
    private val parallelEventConsumer: ParallelStreamProcessor<String, String>,
    private val objectMapper: ObjectMapper,
    private val kafkaTemplate: KafkaTemplate<String, String>,
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
    }

    @PostConstruct
    fun init() {
        parallelEventConsumer.subscribe(listOf(TOPIC))
        parallelEventConsumer.poll { context ->
            val record = context.singleRecord
            val key = record.key()
            val payloadString = record.value()

            val payload =
                try {
                    objectMapper.readValue(
                        payloadString,
                        TimeTableOccupancyReceivedEvent::class.java,
                    )
                } catch (e: JsonProcessingException) {
                    log.error("INVALID JSON", e)

                    onHandleDlt(TOPIC, key, e.toString(), payloadString)
                    return@poll
                }

            repeat(RETRY_ATTEMPTS) { attempt ->
                val isSuccess = runCatching {
                    onEventHandler(payload)
                }
                    .onFailure { error ->
                        log.error("Error in TimeTableOccupancyEvent:", error)
                        log.error("retry: {}", attempt)

                        if (attempt < RETRY_ATTEMPTS - 1) {
                            val backOff = BACK_OFF_DELAY * ((attempt + 1) * BACK_OFF_MULTIPLIER)
                            Thread.sleep(backOff.toLong())
                        }
                    }
                    .isSuccess

                if (isSuccess) return@poll
            }

            onHandleDlt(TOPIC, key, "Max retries exceeded", payloadString)
        }
    }

    @PreDestroy
    fun destroy() {
        parallelEventConsumer.close()
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
            ProducerRecord<String, String>(
                "$originalTopic-$DLT_SUFFIX", // topic
                partitionKey, // key (positional)
                event, // value (positional)
            ).apply {
                headers().add("original-topic", originalTopic.toByteArray())
                headers().add("error-reason", error.toByteArray())
                headers().add("failed-timestamp", Instant.now().toString().toByteArray())
            }

        runCatching {
            kafkaTemplate.send(dltRecord).get(WAIT_FOR_GET_TIME, TimeUnit.SECONDS)
            log.error("Sent to DLT: topic=${dltRecord.topic()}, key=$partitionKey")
        }
            .onFailure { e -> log.error("CRITICAL: Failed to send to DLT", e) }
    }
}
