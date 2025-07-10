package com.reservation.restaurant.vo

import java.time.DayOfWeek

data class RestaurantRoutine(
    val workingDays: MutableList<RestaurantWorkingDay> = mutableListOf(),
) {
    fun add(workingDay: RestaurantWorkingDay) {
        if (workingDays.contains(workingDay)) return
        workingDays.add(workingDay)
    }

    fun remove(day: DayOfWeek) {
        val found = workingDays.find { it.day == day }
        if (found != null) workingDays.remove(found)
    }
}
