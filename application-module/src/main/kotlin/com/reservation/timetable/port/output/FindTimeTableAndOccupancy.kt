package com.reservation.timetable.port.output

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

interface FindTimeTableAndOccupancy {
    fun query(inquiry: FindTimeTableAndOccupancyInquiry): FindTimeTableAndOccupancyResult?

    data class FindTimeTableAndOccupancyInquiry(
        val timeTableId: String,
        val timeTableOccupancyId: String,
    )

    data class FindTimeTableAndOccupancyResult(
        val timeTableId: String,
        val restaurantId: String,
        val date: LocalDate,
        val day: DayOfWeek,
        val startTime: LocalTime,
        val endTime: LocalTime,
        val tableNumber: Int,
        val tableSize: Int,
        val timeTableOccupancyId: String,
        val userId: String,
        val occupiedDatetime: LocalDateTime,
    )
}
