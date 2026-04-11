package com.reservation.persistence.schedule.repository.mutator

import com.reservation.persistence.schedule.entity.HolidayEntity
import com.reservation.persistence.schedule.entity.ScheduleEntity
import com.reservation.persistence.schedule.entity.TableEntity
import com.reservation.persistence.schedule.entity.TimeSpanEntity
import com.reservation.schedule.port.output.LoadSchedule.LoadHolidayResult
import com.reservation.schedule.port.output.LoadSchedule.LoadScheduleResult
import com.reservation.schedule.port.output.LoadSchedule.LoadTableResult
import com.reservation.schedule.port.output.LoadSchedule.LoadTimeSpanResult

object LoadScheduleResultMutator {
    fun mapLoadScheduleResult(
        schedule: ScheduleEntity,
        holidays: List<HolidayEntity>,
        tables: List<TableEntity>,
        timeSpans: List<TimeSpanEntity>,
    ): LoadScheduleResult =
        LoadScheduleResult(
            restaurantId = schedule.restaurantId,
            status = schedule.status,
            timeSpans = timeSpans.map(this::mapLoadTimeSpanResult),
            holidays = holidays.map(this::mapLoadHolidayResult),
            tables = tables.map(this::mapLoadTableResult),
        )

    private fun mapLoadHolidayResult(it: HolidayEntity): LoadHolidayResult =
        LoadHolidayResult(
            id = it.id!!,
            restaurantId = it.restaurantId,
            date = it.date,
        )

    private fun mapLoadTableResult(it: TableEntity): LoadTableResult =
        LoadTableResult(
            id = it.id!!,
            restaurantId = it.restaurantId,
            tableNumber = it.tableNumber,
            tableSize = it.tableSize,
        )

    private fun mapLoadTimeSpanResult(it: TimeSpanEntity): LoadTimeSpanResult =
        LoadTimeSpanResult(
            id = it.id!!,
            restaurantId = it.restaurantId,
            day = it.day,
            startTime = it.startTime,
            endTime = it.endTime,
        )
}
