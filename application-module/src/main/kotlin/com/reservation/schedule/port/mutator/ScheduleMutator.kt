package com.reservation.schedule.port.mutator

import com.reservation.schedule.Holiday
import com.reservation.schedule.Schedule
import com.reservation.schedule.Table
import com.reservation.schedule.TimeSpan
import com.reservation.schedule.port.output.LoadSchedule.LoadHolidayResult
import com.reservation.schedule.port.output.LoadSchedule.LoadScheduleResult
import com.reservation.schedule.port.output.LoadSchedule.LoadTableResult
import com.reservation.schedule.port.output.LoadSchedule.LoadTimeSpanResult

object ScheduleMutator {
    private fun mapTimespan(it: LoadTimeSpanResult) =
        TimeSpan(
            id = it.id,
            restaurantId = it.restaurantId,
            day = it.day,
            startTime = it.startTime,
            endTime = it.endTime,
        )

    private fun mapHoliday(it: LoadHolidayResult) =
        Holiday(
            id = it.id,
            restaurantId = it.restaurantId,
            date = it.date,
        )

    private fun mapTable(it: LoadTableResult) =
        Table(
            id = it.id,
            restaurantId = it.restaurantId,
            tableNumber = it.tableNumber,
            tableSize = it.tableSize,
        )

    fun mutate(result: LoadScheduleResult): Schedule  {
        val timespan = result.timeSpans.map(this::mapTimespan).toMutableList()
        val holiday = result.holidays.map(this::mapHoliday).toMutableList()
        val table = result.tables.map(this::mapTable).toMutableList()

        return Schedule(
            restaurantId = result.restaurantId,
            status = result.status,
            timeSpans = timespan,
            holidays = holiday,
            tables = table,
        )
    }
}
