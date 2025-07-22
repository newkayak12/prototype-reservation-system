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
        val workingDay: List<ChangeWorkingDayInquiry>,
        val tag: List<ChangeTagInquiry>,
        val nationalities: List<ChangeNationalitiesInquiry>,
        val cuisines: List<ChangeCuisinesInquiry>,
        val photos: List<ChangePhotosInquiry>,
    )

    data class ChangeWorkingDayInquiry(
        val day: DayOfWeek,
        val startTime: LocalTime,
        val endTime: LocalTime,
    )

    data class ChangeTagInquiry(
        val tagsId: Long,
    )

    data class ChangeNationalitiesInquiry(
        val nationalitiesId: Long,
    )

    data class ChangeCuisinesInquiry(
        val cuisinesId: Long,
    )

    data class ChangePhotosInquiry(
        val url: String,
    )
}
