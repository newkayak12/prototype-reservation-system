package com.reservation.schedule.port.output

import com.reservation.enumeration.ScheduleActiveStatus
import com.reservation.enumeration.ScheduleActiveStatus.INACTIVE
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

interface LoadSchedule {

    fun query(restaurantId: String): LoadScheduleResult?


    data class LoadScheduleResult(
        val restaurantId: String,
        val status: ScheduleActiveStatus = INACTIVE,
        val timeSpans: List<LoadTimeSpanResult> = listOf(),
        val holidays: List<LoadHolidayResult> = listOf(),
        val tables: List<LoadTableResult> = listOf(),
    )


    data class LoadHolidayResult(
        val id: String,
        val restaurantId: String,
        val date: LocalDate,
    )

    data class LoadTableResult(
        val id: String,
        val restaurantId: String,
        val tableNumber: Int,
        val tableSize: Int,
    )

    data class LoadTimeSpanResult(
        val id: String,
        val restaurantId: String,
        val day: DayOfWeek,
        val startTime: LocalTime,
        val endTime: LocalTime,
    )
}
