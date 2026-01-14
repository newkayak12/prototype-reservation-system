package com.reservation.kafka.listener

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.reservation.httpinterface.exceptions.ResponseBodyRequiredException
import com.reservation.httpinterface.timetable.FindTimeTableOccupancyHttpInterface
import com.reservation.httpinterface.timetable.response.FindTimeTableOccupancyInternallyHttpInterfaceResponse
import com.reservation.kafka.listener.event.TimeTableOccupancyReceivedEvent
import com.reservation.reservation.port.input.CreateReservationUseCase
import com.reservation.reservation.port.input.command.CreateReservationCommand
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class TimeTableOccupancyKafkaListener(
    private val httpInterface: FindTimeTableOccupancyHttpInterface,
    private val createReservationUseCase: CreateReservationUseCase,
) {
    companion object {
        const val TOPIC = "OutboxEventType"
        const val GROUP_ID = "reservation-service"
    }

    @KafkaListener(
        topics = [TOPIC],
        groupId = GROUP_ID,
    )
    fun createReservationHandler(
        @Header(KafkaHeaders.ACKNOWLEDGMENT) ack: Acknowledgment,
        @Payload event: TimeTableOccupancyReceivedEvent,
    ) {
        runCatching {
            val timeTableId = event.timeTableId
            val timeTableOccupancyId = event.timeTableOccupancyId
            val responseEntity =
                httpInterface.findTimeTableOccupancyInternally(
                    timeTableId = timeTableId,
                    timeTableOccupancyId = timeTableOccupancyId,
                )

            createReservation(responseEntity.body)
        }
            .onSuccess { ack.acknowledge() }
            .onFailure { ack.nack(Duration.ofMinutes(1)) }
    }

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
}
