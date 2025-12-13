package com.reservation.rest.internal.timetable.response

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class FindTimeTableOccupancyInternallyResponse(
    val table: FindTimeTableOccupancyTableInformationResponse,
    val book: FindTimeTableOccupancyBookingInformationResponse,
) {
    data class FindTimeTableOccupancyTableInformationResponse(
        val timeTableId: String,
        val restaurantId: String,
        val date: LocalDate,
        val day: DayOfWeek,
        val startTime: LocalTime,
        val endTime: LocalTime,
        val tableNumber: Int,
        val tableSize: Int,
    )

    data class FindTimeTableOccupancyBookingInformationResponse(
        val timeTableOccupancyId: String,
        val userId: String,
        val occupiedDatetime: LocalDateTime,
    )
}
