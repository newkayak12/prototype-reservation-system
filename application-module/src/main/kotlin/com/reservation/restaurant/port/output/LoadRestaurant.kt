package com.reservation.restaurant.port.output

import com.reservation.restaurant.Restaurant
import com.reservation.restaurant.vo.RestaurantAddress
import com.reservation.restaurant.vo.RestaurantContact
import com.reservation.restaurant.vo.RestaurantCoordinate
import com.reservation.restaurant.vo.RestaurantDescription
import com.reservation.restaurant.vo.RestaurantPhoto
import com.reservation.restaurant.vo.RestaurantWorkingDay
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalTime

interface LoadRestaurant {
    fun query(id: String): LoadRestaurantResult?

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
        val workingDay: List<LoadWorkingDayResult>,
        val tag: List<LoadTagResult>,
        val nationalities: List<LoadNationalitiesResult>,
        val cuisines: List<LoadCuisinesResult>,
        val photos: List<LoadPhotosResult>,
    ) {
        fun toDomain(): Restaurant {
            val restaurant =
                Restaurant(
                    id = id,
                    companyId = companyId,
                    userId = userId,
                    introduce = RestaurantDescription(name, introduce),
                    contact = RestaurantContact(phone),
                    address =
                        RestaurantAddress(
                            zipCode = zipCode,
                            address = address,
                            detail = detail,
                            coordinate =
                                RestaurantCoordinate(
                                    latitude = latitude,
                                    longitude = longitude,
                                ),
                        ),
                )

            restaurant.manipulateRoutine {
                workingDay
                    .map { workingDay ->
                        RestaurantWorkingDay(
                            workingDay.day,
                            workingDay.startTime,
                            workingDay.endTime,
                        )
                    }
                    .forEach(it::add)
            }

            restaurant.manipulatePhoto {
                photos
                    .map { photo -> photo.url }
                    .map { photoUrl -> RestaurantPhoto(photoUrl) }
                    .forEach(it::add)
            }

            restaurant.manipulateTags {
                tag
                    .map { tag -> tag.tagsId }
                    .forEach(it::add)
            }
            restaurant.manipulateNationalities {
                nationalities
                    .map { nationality -> nationality.nationalitiesId }
                    .forEach(it::add)
            }
            restaurant.manipulateCuisines {
                cuisines
                    .map { cuisines -> cuisines.cuisinesId }
                    .forEach(it::add)
            }

            return restaurant
        }
    }

    data class LoadWorkingDayResult(
        val day: DayOfWeek,
        val startTime: LocalTime,
        val endTime: LocalTime,
    )

    data class LoadTagResult(
        val tagsId: Long,
    )

    data class LoadNationalitiesResult(
        val nationalitiesId: Long,
    )

    data class LoadCuisinesResult(
        val cuisinesId: Long,
    )

    data class LoadPhotosResult(
        val url: String,
    )
}
