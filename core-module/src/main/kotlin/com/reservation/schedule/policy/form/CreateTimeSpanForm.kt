package com.reservation.schedule.policy.form

import java.time.DayOfWeek
import java.time.LocalTime

data class CreateTimeSpanForm(
    val restaurantId: String,
    val day: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
)
