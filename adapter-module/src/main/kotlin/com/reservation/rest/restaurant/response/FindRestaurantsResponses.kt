package com.reservation.rest.restaurant.response

import com.reservation.enumeration.CategoryType
import com.reservation.restaurant.port.input.query.response.FindRestaurantsQueryResults
import com.reservation.restaurant.port.input.query.response.FindRestaurantsQueryResults.FindRestaurantsQueryResult
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalTime

data class FindRestaurantsResponses(
    val list: List<FindRestaurantsResponse> = listOf(),
    val hasNext: Boolean = false,
) {
    companion object {
        fun from(results: FindRestaurantsQueryResults): FindRestaurantsResponses =
            FindRestaurantsResponses(
                results.list.map { FindRestaurantsResponse.from(it) },
                results.hasNext,
            )
    }

    data class FindRestaurantsResponse(
        val identifier: String,
        val information: FindRestaurantsInformationResponse,
        val address: FindRestaurantsAddressResponse,
        val workingDays: List<FindRestaurantsWorkingDayResponse> = listOf(),
        val photos: List<FindRestaurantsPhotoResponse> = listOf(),
        val tags: List<FindRestaurantsCategoryResponse> = listOf(),
        val nationalities: List<FindRestaurantsCategoryResponse> = listOf(),
        val cuisines: List<FindRestaurantsCategoryResponse> = listOf(),
    ) {
        companion object {
            private fun mapInformation(
                it: FindRestaurantsQueryResult,
            ): FindRestaurantsInformationResponse =
                FindRestaurantsInformationResponse(
                    name = it.information.name,
                    introduce = it.information.introduce,
                )

            private fun mapAddress(it: FindRestaurantsQueryResult): FindRestaurantsAddressResponse =
                FindRestaurantsAddressResponse(
                    zipCode = it.address.zipCode,
                    address = it.address.address,
                    detail = it.address.detail,
                    coordinate =
                        FindRestaurantsCoordinateResponse(
                            latitude = it.address.coordinate.latitude,
                            longitude = it.address.coordinate.longitude,
                        ),
                )

            private fun mapWorkingDays(
                it: FindRestaurantsQueryResult,
            ): List<FindRestaurantsWorkingDayResponse> =
                it.workingDays.map { workingDay ->
                    FindRestaurantsWorkingDayResponse(
                        day = workingDay.day,
                        startTime = workingDay.startTime,
                        endTime = workingDay.endTime,
                    )
                }

            private fun mapPhotos(
                it: FindRestaurantsQueryResult,
            ): List<FindRestaurantsPhotoResponse> =
                it.photos.map { photo -> FindRestaurantsPhotoResponse(photo.url) }

            private fun mapCategories(
                it: FindRestaurantsQueryResult,
            ): List<FindRestaurantsCategoryResponse> =
                it.tags.map { tag ->
                    FindRestaurantsCategoryResponse(
                        id = tag.id,
                        title = tag.title,
                        categoryType = tag.categoryType,
                    )
                }

            fun from(it: FindRestaurantsQueryResult): FindRestaurantsResponse {
                return FindRestaurantsResponse(
                    identifier = it.identifier,
                    information = mapInformation(it),
                    address = mapAddress(it),
                    workingDays = mapWorkingDays(it),
                    photos = mapPhotos(it),
                    tags = mapCategories(it),
                    nationalities = mapCategories(it),
                    cuisines = mapCategories(it),
                )
            }
        }
    }

    data class FindRestaurantsWorkingDayResponse(
        val day: DayOfWeek,
        val startTime: LocalTime,
        val endTime: LocalTime,
    )

    data class FindRestaurantsAddressResponse(
        val zipCode: String,
        val address: String,
        val detail: String,
        val coordinate: FindRestaurantsCoordinateResponse,
    )

    data class FindRestaurantsCoordinateResponse(
        val latitude: BigDecimal,
        val longitude: BigDecimal,
    )

    data class FindRestaurantsPhotoResponse(
        val url: String,
    )

    data class FindRestaurantsInformationResponse(
        val name: String,
        val introduce: String,
    )

    data class FindRestaurantsCategoryResponse(
        val id: Long,
        val title: String,
        val categoryType: CategoryType,
    )
}
