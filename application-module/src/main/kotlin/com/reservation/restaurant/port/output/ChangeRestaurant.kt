package com.reservation.restaurant.port.output

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalTime

fun interface ChangeRestaurant {
    fun execute(inquiry: ChangeRestaurantInquiry): Boolean

    data class ChangeRestaurantInquiry(
        val id: String,
        val name: String,
        val introduce: String,
        val phone: String,
        val zipCode: String,
        val address: String,
        val detail: String,
        val latitude: BigDecimal,
        val longitude: BigDecimal,
        val workingDay: List<ChangeWorkingDayResult>,
        val tag: List<ChangeTagResult>,
        val nationalities: List<ChangeNationalitiesResult>,
        val cuisines: List<ChangeCuisinesResult>,
        val photos: List<ChangePhotosResult>,
    )

    data class ChangeWorkingDayResult(
        val day: DayOfWeek,
        val startTime: LocalTime,
        val endTime: LocalTime,
    )

    data class ChangeTagResult(
        val tagsId: Long,
    )

    data class ChangeNationalitiesResult(
        val nationalitiesId: Long,
    )

    data class ChangeCuisinesResult(
        val cuisinesId: Long,
    )

    data class ChangePhotosResult(
        val url: String,
    )
}
