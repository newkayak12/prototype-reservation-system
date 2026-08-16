package com.reservation.command.core.reservation.command

import com.reservation.command.core.support.Command
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Suppress("LongParameterList")
data class CreateReservation(
    val reservationId: String,
    val userId: String,
    val restaurantId: String,
    val tableNumber: Int,
    val tableSize: Int,
    val slotId: String,
    val date: LocalDate,
    val day: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val requestedAt: LocalDateTime,
) : Command
