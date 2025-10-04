package com.reservation.schedule.port.mutator

import com.reservation.schedule.port.output.ChangeSchedule.HolidayInquiry
import com.reservation.schedule.port.output.ChangeSchedule.ScheduleInquiry
import com.reservation.schedule.port.output.ChangeSchedule.TableInquiry
import com.reservation.schedule.port.output.ChangeSchedule.TimeSpanInquiry
import com.reservation.schedule.snapshot.HolidaySnapshot
import com.reservation.schedule.snapshot.ScheduleSnapshot
import com.reservation.schedule.snapshot.TableSnapshot
import com.reservation.schedule.snapshot.TimeSpanSnapshot

object ScheduleInquiryMutator {
    private fun mapTimespan(it: TimeSpanSnapshot) =
        TimeSpanInquiry(
            id = it.id,
            restaurantId = it.restaurantId,
            day = it.day,
            startTime = it.startTime,
            endTime = it.endTime,
        )

    private fun mapHoliday(it: HolidaySnapshot) =
        HolidayInquiry(
            id = it.id,
            restaurantId = it.restaurantId,
            date = it.date,
        )

    private fun mapTable(it: TableSnapshot) =
        TableInquiry(
            id = it.id,
            restaurantId = it.restaurantId,
            tableNumber = it.tableNumber,
            tableSize = it.tableSize,
        )

    fun mutate(result: ScheduleSnapshot): ScheduleInquiry {
        val timespan = result.timeSpans.map(this::mapTimespan).toMutableList()
        val holiday = result.holidays.map(this::mapHoliday).toMutableList()
        val table = result.tables.map(this::mapTable).toMutableList()

        return ScheduleInquiry(
            restaurantId = result.restaurantId,
            status = result.status,
            timeSpans = timespan,
            holidays = holiday,
            tables = table,
        )
    }
}
