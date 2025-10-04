package com.reservation.schedule.snapshot

import com.reservation.enumeration.ScheduleActiveStatus

class ScheduleSnapshot(
    val restaurantId: String,
    val status: ScheduleActiveStatus,
    val timeSpans: List<TimeSpanSnapshot> = listOf(),
    val holidays: List<HolidaySnapshot> = listOf(),
    val tables: List<TableSnapshot> = listOf(),
)
