package com.reservation.rest.timetable.request

import java.time.LocalDate
import java.time.LocalTime

data class CreateTimeTableOccupancyRequest(
    val date: LocalDate,
    val startTime: LocalTime,
)
