package com.reservation.schedule.port.input.command.request

import java.time.DayOfWeek
import java.time.LocalTime

data class CreateTimeSpanCommand(
    val restaurantId: String,
    val day: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
)
