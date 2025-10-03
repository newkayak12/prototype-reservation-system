package com.reservation.schedule

import com.reservation.schedule.snapshot.ScheduleSnapshot

class Schedule(
    private val restaurantId: String,
) {
    private val timeSpans: MutableList<TimeSpan> = mutableListOf()
    private val holidays: MutableList<Holiday> = mutableListOf()

    fun addTimeSpan(timeSpan: TimeSpan) {
        if (timeSpans.contains(timeSpan)) return;

        timeSpans.add(timeSpan)
    }

    fun addHoliday(holiday: Holiday) {
        if (holidays.contains(holiday)) return;

        holidays.add(holiday)
    }


    fun snapshot() = ScheduleSnapshot(
        restaurantId = restaurantId,
        timeSpans = timeSpans.map { it.snapshot() },
        holidays = holidays.map { it.snapshot() }
    )
}
