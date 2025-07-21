package com.reservation.restaurant.port.output

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalTime

interface LoadRestaurant {
    data class LoadRestaurantResult(
        val id: String,
        val companyId: String,
        val userId: String,
        val name: String,
        val introduce: String,
        val phone: String,
        val zipCode: String,
        val address: String,
        val detail: String,
        val latitude: BigDecimal,
        val longitude: BigDecimal,
    )

    data class LoadWorkingDayResult(
        val identifier: String,
        val day: DayOfWeek,
        val startTime: LocalTime,
        val endTime: LocalTime,
    )
}
