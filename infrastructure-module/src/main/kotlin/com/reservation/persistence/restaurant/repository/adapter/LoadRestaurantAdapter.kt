package com.reservation.persistence.restaurant.repository.adapter

import com.reservation.persistence.restaurant.repository.jpa.RestaurantJpaRepository
import com.reservation.restaurant.port.output.LoadRestaurant
import com.reservation.restaurant.port.output.LoadRestaurant.LoadCuisinesResult
import com.reservation.restaurant.port.output.LoadRestaurant.LoadNationalitiesResult
import com.reservation.restaurant.port.output.LoadRestaurant.LoadPhotosResult
import com.reservation.restaurant.port.output.LoadRestaurant.LoadRestaurantResult
import com.reservation.restaurant.port.output.LoadRestaurant.LoadTagResult
import com.reservation.restaurant.port.output.LoadRestaurant.LoadWorkingDayResult
import org.springframework.stereotype.Component

@Component
class LoadRestaurantAdapter(
    private val jpaRepository: RestaurantJpaRepository,
) : LoadRestaurant {
    override fun query(id: String): LoadRestaurantResult? {
        return jpaRepository.findRestaurantEntityById(id)
            .map {
                LoadRestaurantResult(
                    id = it.id!!,
                    companyId = it.companyId,
                    userId = it.userId,
                    name = it.name,
                    introduce = it.introduce,
                    phone = it.phone,
                    zipCode = it.zipCode,
                    address = it.address,
                    detail = it.detail,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    workingDay =
                        it.workingDaysAll().map { day ->
                            LoadWorkingDayResult(day.id.day, day.startTime, day.endTime)
                        },
                    tag = it.tagsAll().map { tag -> LoadTagResult(tag.tagsId) },
                    nationalities =
                        it.nationalitiesAll().map { nationality ->
                            LoadNationalitiesResult(nationality.nationalitiesId)
                        },
                    cuisines =
                        it.cuisinesAll().map { cuisine ->
                            LoadCuisinesResult(cuisine.cuisinesId)
                        },
                    photos =
                        it.photosAll().map { photo ->
                            LoadPhotosResult(photo.url)
                        },
                )
            }
            .orElse(null)
    }
}
