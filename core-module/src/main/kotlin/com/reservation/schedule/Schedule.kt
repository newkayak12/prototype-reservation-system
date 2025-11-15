package com.reservation.schedule

import com.reservation.enumeration.ScheduleActiveStatus
import com.reservation.enumeration.ScheduleActiveStatus.INACTIVE
import com.reservation.schedule.snapshot.ScheduleSnapshot

class Schedule(
    private val restaurantId: String,
    private val status: ScheduleActiveStatus = INACTIVE,
    private val timeSpans: MutableList<TimeSpan> = mutableListOf(),
    private val holidays: MutableList<Holiday> = mutableListOf(),
    private val tables: MutableList<Table> = mutableListOf(),
) {
    fun addHoliday(holiday: Holiday) {
        if (holidays.contains(holiday)) return

        holidays.add(holiday)
    }

    fun addTimeSpan(timeSpan: TimeSpan) {
        if (timeSpans.contains(timeSpan)) return

        timeSpans.add(timeSpan)
    }

    fun snapshot() =
        ScheduleSnapshot(
            restaurantId = restaurantId,
            status = status,
            timeSpans = timeSpans.map { it.snapshot() },
            holidays = holidays.map { it.snapshot() },
            tables = tables.map { it.snapshot() },
        )
}
