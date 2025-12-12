package com.reservation.event.timetable.occupancy

data class TimeTableOccupiedOutboxEvent(
    val outboxId: Long,
    val event: TimeTableOccupiedEvent,
)
