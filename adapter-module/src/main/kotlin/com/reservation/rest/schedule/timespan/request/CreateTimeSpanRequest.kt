package com.reservation.rest.schedule.timespan.request

import java.time.DayOfWeek
import java.time.LocalTime

data class CreateTimeSpanRequest(
    val day: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
)
