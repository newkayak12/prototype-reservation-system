package com.reservation.schedule.port.output

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

interface CreateSchedule {

    fun command(command: CreateScheduleInquiry): Boolean

    class CreateScheduleInquiry(
        val restaurantId: String,
        val timeSpans: List<CreateTimeSpanInquiry> = listOf(),
        val holidays: List<CreateHolidayInquiry> = listOf(),
    )

    class CreateTimeSpanInquiry(
        val id: String? = null,
        val restaurantId: String,
        val day: DayOfWeek,
        val startTime: LocalTime,
        val endTime: LocalTime,
    )

    class CreateHolidayInquiry(
        val id: String? = null,
        val restaurantId: String,
        val date: LocalDate,
    )
}
