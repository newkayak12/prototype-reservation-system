package com.reservation.httpinterface.timetable.response

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class FindTimeTableOccupancyInternallyHttpInterfaceResponse(
    val table: InformationHttpInterfaceResponse,
    val book: BookingInformationHttpInterfaceResponse,
) {
    data class InformationHttpInterfaceResponse(
        val timeTableId: String,
        val restaurantId: String,
        val date: LocalDate,
        val day: DayOfWeek,
        val startTime: LocalTime,
        val endTime: LocalTime,
        val tableNumber: Int,
        val tableSize: Int,
    )

    data class BookingInformationHttpInterfaceResponse(
        val userId: String,
        val timeTableOccupancyId: String,
        val occupiedDatetime: LocalDateTime,
    )
}
