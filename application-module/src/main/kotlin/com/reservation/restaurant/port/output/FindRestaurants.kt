package com.reservation.restaurant.port.output

import com.reservation.restaurant.port.input.query.response.FindRestaurantsQueryResults.FindRestaurantsQueryResult
import com.reservation.restaurant.port.input.query.response.FindRestaurantsQueryResults.FindRestaurantsQueryResultAddress
import com.reservation.restaurant.port.input.query.response.FindRestaurantsQueryResults.FindRestaurantsQueryResultCategory
import com.reservation.restaurant.port.input.query.response.FindRestaurantsQueryResults.FindRestaurantsQueryResultCoordinate
import com.reservation.restaurant.port.input.query.response.FindRestaurantsQueryResults.FindRestaurantsQueryResultInformation
import com.reservation.restaurant.port.input.query.response.FindRestaurantsQueryResults.FindRestaurantsQueryResultPhoto
import com.reservation.restaurant.port.input.query.response.FindRestaurantsQueryResults.FindRestaurantsQueryResultWorkingDay
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalTime

interface FindRestaurants {
    fun query(inquiry: FindRestaurantsInquiry): List<FindRestaurantsResult>

    data class FindRestaurantsInquiry(
        val identifierFrom: String = "",
        val size: Long = 10,
        val searchText: String = "",
        val tags: List<Long> = listOf(),
        val nationalities: List<Long> = listOf(),
        val cuisines: List<Long> = listOf(),
    )

    @Suppress("LongParameterList")
    class FindRestaurantsResult(
        val identifier: String,
        val information: FindRestaurantsResultInformation,
        val address: FindRestaurantsResultAddress,
        val workingDays: List<FindRestaurantsResultWorkingDay> = listOf(),
        val photos: List<FindRestaurantsResultPhoto> = listOf(),
        val tags: List<Long> = listOf(),
        val nationalities: List<Long> = listOf(),
        val cuisines: List<Long> = listOf(),
    ) {
        fun toQuery(
            tags: List<FindRestaurantsQueryResultCategory>,
            nationalities: List<FindRestaurantsQueryResultCategory>,
            cuisines: List<FindRestaurantsQueryResultCategory>,
        ): FindRestaurantsQueryResult {
            return FindRestaurantsQueryResult(
                identifier,
                information.toQuery(),
                address.toQuery(),
                workingDays.map { it.toQuery() },
                photos.map { it.toQuery() },
                tags,
                nationalities,
                cuisines,
            )
        }
    }

    data class FindRestaurantsResultWorkingDay(
        val day: DayOfWeek,
        val startTime: LocalTime,
        val endTime: LocalTime,
    ) {
        fun toQuery(): FindRestaurantsQueryResultWorkingDay {
            return FindRestaurantsQueryResultWorkingDay(day, startTime, endTime)
        }
    }

    data class FindRestaurantsResultAddress(
        val zipCode: String,
        val address: String,
        val detail: String,
        val coordinate: FindRestaurantsResultCoordinate,
    ) {
        fun toQuery(): FindRestaurantsQueryResultAddress {
            return FindRestaurantsQueryResultAddress(
                zipCode,
                address,
                detail,
                coordinate.toQuery(),
            )
        }
    }

    data class FindRestaurantsResultCoordinate(
        val latitude: BigDecimal,
        val longitude: BigDecimal,
    ) {
        fun toQuery(): FindRestaurantsQueryResultCoordinate {
            return FindRestaurantsQueryResultCoordinate(latitude, longitude)
        }
    }

    data class FindRestaurantsResultPhoto(
        val url: String,
    ) {
        fun toQuery(): FindRestaurantsQueryResultPhoto {
            return FindRestaurantsQueryResultPhoto(url)
        }
    }

    data class FindRestaurantsResultInformation(
        val name: String,
        val introduce: String,
    ) {
        fun toQuery(): FindRestaurantsQueryResultInformation {
            return FindRestaurantsQueryResultInformation(name, introduce)
        }
    }
}
