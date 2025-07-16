package com.reservation.restaurant.policy.format

import java.time.DayOfWeek
import java.time.LocalTime

data class RestaurantWorkingDayForm(
    val day: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
)
