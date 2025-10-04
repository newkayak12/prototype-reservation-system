package com.reservation.schedule.port.output

import com.reservation.enumeration.ScheduleActiveStatus
import com.reservation.enumeration.ScheduleActiveStatus.INACTIVE
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

interface ChangeSchedule {


    fun command(inquiry: ScheduleInquiry): Boolean



    data class ScheduleInquiry(
        val restaurantId: String,
        val status: ScheduleActiveStatus = INACTIVE,
        val timeSpans: List<TimeSpanInquiry> = listOf(),
        val holidays: List<HolidayInquiry> = listOf(),
        val tables: List<TableInquiry> = listOf(),
    )


    data class HolidayInquiry(
        val id: String? = null,
        val restaurantId: String,
        val date: LocalDate,
    )

    data class TableInquiry(
        val id: String? = null,
        val restaurantId: String,
        val tableNumber: Int,
        val tableSize: Int,
    )

    data class TimeSpanInquiry(
        val id: String? = null,
        val restaurantId: String,
        val day: DayOfWeek,
        val startTime: LocalTime,
        val endTime: LocalTime,
    )
}
