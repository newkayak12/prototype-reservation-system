package com.reservation.timetable.port.input.command.request

import java.time.LocalDate
import java.time.LocalTime

data class CreateTimeTableOccupancyCommand(
    val userId: String,
    val restaurantId: String,
    val date: LocalDate,
    val startTime: LocalTime,
)
