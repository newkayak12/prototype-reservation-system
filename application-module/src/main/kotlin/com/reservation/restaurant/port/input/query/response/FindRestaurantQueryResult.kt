package com.reservation.restaurant.port.input.query.response

import com.reservation.enumeration.CategoryType
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalTime

data class FindRestaurantQueryResult(
    val identifier: String,
    val company: FindRestaurantQueryResultCompany,
    val userId: String,
    val information: FindRestaurantQueryResultInformation,
    val phone: String,
    val address: FindRestaurantQueryResultAddress,
    val workingDays: List<FindRestaurantQueryResultWorkingDay> = listOf(),
    val photos: List<FindRestaurantQueryResultPhoto> = listOf(),
    val tags: List<FindRestaurantQueryResultTag> = listOf(),
    val nationalities: List<FindRestaurantQueryResultTag> = listOf(),
    val cuisines: List<FindRestaurantQueryResultTag> = listOf(),
) {
    data class FindRestaurantQueryResultCompany(
        val companyId: String,
        val companyName: String,
    )

    data class FindRestaurantQueryResultInformation(
        val name: String,
        val introduce: String,
    )

    data class FindRestaurantQueryResultAddress(
        val zipCode: String,
        val address: String,
        val detail: String,
        val coordinate: FindRestaurantQueryResultCoordinate,
    )

    data class FindRestaurantQueryResultCoordinate(
        val latitude: BigDecimal,
        val longitude: BigDecimal,
    )

    data class FindRestaurantQueryResultWorkingDay(
        val day: DayOfWeek,
        val startTime: LocalTime,
        val endTime: LocalTime,
    )

    data class FindRestaurantQueryResultPhoto(
        val url: String,
    )

    data class FindRestaurantQueryResultTag(
        val id: Long,
        val title: String,
        val categoryType: CategoryType,
    )
}
