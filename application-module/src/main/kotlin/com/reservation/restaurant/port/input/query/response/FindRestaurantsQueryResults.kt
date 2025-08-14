package com.reservation.restaurant.port.input.query.response

import com.reservation.enumeration.CategoryType
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalTime

class FindRestaurantsQueryResults(
    val list: List<FindRestaurantsQueryResult> = listOf(),
    val hasNext: Boolean = false,
) {
    companion object {
        private const val DEFAULT_FETCH_SIZE = 10

        fun from(
            list: List<FindRestaurantsQueryResult>,
            fetchSize: Int = DEFAULT_FETCH_SIZE,
        ): FindRestaurantsQueryResults {
            return FindRestaurantsQueryResults(
                list.take(DEFAULT_FETCH_SIZE),
                isNextPageExists(list.size, fetchSize),
            )
        }

        private fun isNextPageExists(
            count: Int,
            fetchSize: Int,
        ) = count > fetchSize
    }

    data class FindRestaurantsQueryResult(
        val identifier: String,
        val information: FindRestaurantsQueryResultInformation,
        val address: FindRestaurantsQueryResultAddress,
        val workingDays: List<FindRestaurantsQueryResultWorkingDay> = listOf(),
        val photos: List<FindRestaurantsQueryResultPhoto> = listOf(),
        val tags: List<FindRestaurantsQueryResultCategory> = listOf(),
        val nationalities: List<FindRestaurantsQueryResultCategory> = listOf(),
        val cuisines: List<FindRestaurantsQueryResultCategory> = listOf(),
    )

    data class FindRestaurantsQueryResultWorkingDay(
        val day: DayOfWeek,
        val startTime: LocalTime,
        val endTime: LocalTime,
    )

    data class FindRestaurantsQueryResultAddress(
        val zipCode: String,
        val address: String,
        val detail: String,
        val coordinate: FindRestaurantsQueryResultCoordinate,
    )

    data class FindRestaurantsQueryResultCoordinate(
        val latitude: BigDecimal,
        val longitude: BigDecimal,
    )

    data class FindRestaurantsQueryResultPhoto(
        val url: String,
    )

    data class FindRestaurantsQueryResultInformation(
        val name: String,
        val introduce: String,
    )

    data class FindRestaurantsQueryResultCategory(
        val id: Long,
        val title: String,
        val categoryType: CategoryType,
    )
}
