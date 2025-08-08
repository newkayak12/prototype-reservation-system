package com.reservation.restaurant.port.output

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalTime

interface FindRestaurant {
    fun query(id: String): FindRestaurantResult?

    data class FindRestaurantResult(
        val identifier: String,
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
        val workingDays: List<FindRestaurantWorkingDay>,
        val photos: List<FindRestaurantPhoto>,
        val tags: List<Long>,
        val nationalities: List<Long>,
        val cuisines: List<Long>,
    )

    data class FindRestaurantWorkingDay(
        val day: DayOfWeek,
        val startTime: LocalTime,
        val endTime: LocalTime,
    )

    data class FindRestaurantPhoto(
        val url: String,
    )
}
