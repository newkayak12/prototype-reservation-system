package com.reservation.schedule.port.output

import com.reservation.enumeration.ScheduleActiveStatus
import com.reservation.enumeration.ScheduleActiveStatus.INACTIVE
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

interface CreateSchedule {
    fun command(command: CreateScheduleInquiry): Boolean

    class CreateScheduleInquiry(
        val restaurantId: String,
        val status: ScheduleActiveStatus = INACTIVE,
        val timeSpans: List<CreateTimeSpanInquiry> = listOf(),
        val holidays: List<CreateHolidayInquiry> = listOf(),
        val tables: List<CreateTableInquiry> = listOf(),
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

    class CreateTableInquiry(
        val id: String? = null,
        val restaurantId: String,
        val tableNumber: Int,
        val tableSize: Int,
    )
}
