package com.reservation.reservation.policy.format

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class CreateReservationForm(
    val restaurantId: String,
    val userId: String,
    val timeTableId: String,
    val timeTableOccupancyId: String,
    val date: LocalDate,
    val day: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val tableNumber: Int,
    val tableSize: Int,
    val occupiedDatetime: LocalDateTime,
)
