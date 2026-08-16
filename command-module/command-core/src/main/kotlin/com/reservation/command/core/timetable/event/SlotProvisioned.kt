package com.reservation.command.core.timetable.event

import com.reservation.command.core.support.DomainEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Suppress("LongParameterList")
data class SlotProvisioned(
    val slotId: String,
    val restaurantId: String,
    val date: LocalDate,
    val day: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val tableNumber: Int,
    val tableSize: Int,
) : DomainEvent
