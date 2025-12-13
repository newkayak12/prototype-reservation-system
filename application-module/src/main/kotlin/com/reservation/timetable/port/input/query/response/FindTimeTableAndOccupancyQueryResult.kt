package com.reservation.timetable.port.input.query.response

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class FindTimeTableAndOccupancyQueryResult(
    val timeTableId: String,
    val restaurantId: String,
    val date: LocalDate,
    val day: DayOfWeek,
    val endTime: LocalTime,
    val tableNumber: Int,
    val tableSize: Int,
    val timeTableOccupancyId: String,
    val userId: String,
    val occupiedDatetime: LocalDateTime,
)
