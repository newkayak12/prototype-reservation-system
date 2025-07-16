package com.reservation.restaurant.vo

import java.time.DayOfWeek
import java.time.LocalTime

data class RestaurantWorkingDay(
    val day: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RestaurantWorkingDay) return false

        if (day != other.day) return false

        return true
    }

    override fun hashCode(): Int {
        return day.hashCode()
    }
}
