package com.reservation.kafka.listener

import com.reservation.httpinterface.timetable.FindTimeTableOccupancyHttpInterface
import com.reservation.kafka.listener.event.TimeTableOccupancyReceivedEvent
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

@Component
class TimeTableOccupancyKafkaListener(
    private val httpInterface: FindTimeTableOccupancyHttpInterface,
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
        }
            .onSuccess { ack.acknowledge() }
    }
}
