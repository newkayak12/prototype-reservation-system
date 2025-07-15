package com.reservation.persistence.restaurant.repository.adapter

import com.reservation.persistence.restaurant.RestaurantEntity
import com.reservation.persistence.restaurant.repository.jpa.RestaurantJpaRepository
import com.reservation.restaurant.port.output.CreateRestaurant
import com.reservation.restaurant.port.output.CreateRestaurant.CreateProductInquiry
import com.reservation.restaurant.port.output.CreateRestaurant.Photo
import com.reservation.restaurant.port.output.CreateRestaurant.WorkingDay
import org.springframework.stereotype.Component

@Component
class CreateRestaurantAdapter(
    private val jpaRepository: RestaurantJpaRepository,
) : CreateRestaurant {
    override fun command(inquiry: CreateProductInquiry): Boolean {
        val entity =
            RestaurantEntity(
                companyId = inquiry.companyId,
                userId = inquiry.userId,
                name = inquiry.name,
                introduce = inquiry.introduce,
                phone = inquiry.phone,
                zipCode = inquiry.zipCode,
                address = inquiry.address,
                detail = inquiry.detail,
                latitude = inquiry.latitude,
                longitude = inquiry.longitude,
            )

        addWorkingDays(entity, inquiry.workingDays)
        addPhotos(entity, inquiry.photos)
        addCategories(entity, inquiry.tags + inquiry.nationalities + inquiry.cuisines)

        val result = jpaRepository.save(entity)
        return result.isPersisted()
    }

    private fun addWorkingDays(
        entity: RestaurantEntity,
        workingDays: List<WorkingDay>,
    ) {
        if (workingDays.isEmpty()) {
            return
        }

        for ((day, startTime, endTime) in workingDays) {
            entity.addWeekDay(day, startTime, endTime)
        }
    }

    private fun addPhotos(
        entity: RestaurantEntity,
        photos: List<Photo>,
    ) {
        if (photos.isEmpty()) {
            return
        }

        for ((url) in photos) {
            entity.addPhoto(url)
        }
    }

    private fun addCategories(
        entity: RestaurantEntity,
        categories: List<Long>,
    ) {
        if (categories.isEmpty()) {
            return
        }

        for (category in categories) {
            entity.addCategory(category)
        }
    }
}
