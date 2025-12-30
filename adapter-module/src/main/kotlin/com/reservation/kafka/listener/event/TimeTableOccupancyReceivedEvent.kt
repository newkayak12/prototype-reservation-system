package com.reservation.kafka.listener.event

import com.reservation.enumeration.OutboxEventType
import java.time.LocalDateTime

data class TimeTableOccupancyReceivedEvent(
    val eventType: OutboxEventType,
    val eventVersion: Double,
    val timeTableId: String,
    val timeTableOccupancyId: String,
    val eventId: String,
    val occurredAt: LocalDateTime,
)
