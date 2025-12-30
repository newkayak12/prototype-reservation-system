package com.reservation.event.timetable.occupancy

import com.reservation.enumeration.OutboxEventType
import com.reservation.event.abstractEvent.TimeTableOccupancyEvent
import com.reservation.utilities.generator.uuid.UuidGenerator
import java.time.LocalDateTime

data class TimeTableOccupiedEvent(
    override val eventType: OutboxEventType,
    override val eventVersion: Double,
    val timeTableId: String,
    val timeTableOccupancyId: String,
) : TimeTableOccupancyEvent {
    val eventId = UuidGenerator.generate()
    val occurredAt = LocalDateTime.now()

    override fun key(): String = "${timeTableId}_$timeTableOccupancyId"
}
