package com.reservation.rest.restaurant.response

import com.reservation.enumeration.CategoryType
import com.reservation.restaurant.port.input.query.response.FindRestaurantQueryResult
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalTime

data class FindRestaurantResponse(
    val identifier: String,
    val company: FindRestaurantCompanyResponse,
    val userId: String,
    val information: FindRestaurantInformationResponse,
    val phone: String,
    val address: FindRestaurantAddressResponse,
    val workingDays: List<FindRestaurantWorkingDayResponse> = listOf(),
    val photos: List<FindRestaurantPhotoResponse> = listOf(),
    val tags: List<FindRestaurantTagResponse> = listOf(),
    val nationalities: List<FindRestaurantTagResponse> = listOf(),
    val cuisines: List<FindRestaurantTagResponse> = listOf(),
) {
    companion object {
        private fun mapCategory(
            id: Long,
            title: String,
            categoryType: CategoryType,
        ) = FindRestaurantTagResponse(id, title, categoryType)

        fun from(result: FindRestaurantQueryResult): FindRestaurantResponse {
            val company =
                FindRestaurantCompanyResponse(
                    companyId = result.company.companyId,
                    companyName = result.company.companyName,
                )

            val information =
                FindRestaurantInformationResponse(
                    name = result.information.name,
                    introduce = result.information.introduce,
                )

            val address =
                FindRestaurantAddressResponse(
                    zipCode = result.address.zipCode,
                    address = result.address.address,
                    detail = result.address.detail,
                    coordinate =
                        FindRestaurantCoordinateResponse(
                            latitude = result.address.coordinate.latitude,
                            longitude = result.address.coordinate.longitude,
                        ),
                )
            val workDays =
                result.workingDays.map {
                    FindRestaurantWorkingDayResponse(
                        it.day,
                        it.startTime,
                        it.endTime,
                    )
                }

            val tags = result.tags.map { mapCategory(it.id, it.title, it.categoryType) }
            val nationalities =
                result.nationalities.map {
                    mapCategory(
                        it.id,
                        it.title,
                        it.categoryType,
                    )
                }
            val cuisines = result.cuisines.map { mapCategory(it.id, it.title, it.categoryType) }

            return FindRestaurantResponse(
                identifier = result.identifier,
                company = company,
                userId = result.userId,
                information = information,
                phone = result.phone,
                address = address,
                workingDays = workDays,
                photos = result.photos.map { FindRestaurantPhotoResponse(it.url) },
                tags = tags,
                nationalities = nationalities,
                cuisines = cuisines,
            )
        }
    }

    data class FindRestaurantCompanyResponse(
        val companyId: String,
        val companyName: String,
    )

    data class FindRestaurantInformationResponse(
        val name: String,
        val introduce: String,
    )

    data class FindRestaurantAddressResponse(
        val zipCode: String,
        val address: String,
        val detail: String,
        val coordinate: FindRestaurantCoordinateResponse,
    )

    data class FindRestaurantCoordinateResponse(
        val latitude: BigDecimal,
        val longitude: BigDecimal,
    )

    data class FindRestaurantWorkingDayResponse(
        val day: DayOfWeek,
        val startTime: LocalTime,
        val endTime: LocalTime,
    )

    data class FindRestaurantPhotoResponse(
        val url: String,
    )

    data class FindRestaurantTagResponse(
        val id: Long,
        val title: String,
        val categoryType: CategoryType,
    )
}
