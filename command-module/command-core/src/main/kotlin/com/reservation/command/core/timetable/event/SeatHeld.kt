package com.reservation.command.core.timetable.event

import com.reservation.command.core.support.DomainEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Suppress("LongParameterList")
data class SeatHeld(
    val slotId: String,
    val reservationId: String,
    val userId: String,
    val restaurantId: String,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val tableNumber: Int,
    val heldAt: Instant,
    val holdExpiresAt: Instant,
) : DomainEvent
