package com.reservation.persistence.restaurant.repository.adapter

import com.reservation.persistence.restaurant.entity.RestaurantEntity
import com.reservation.persistence.restaurant.repository.jpa.RestaurantJpaRepository
import com.reservation.persistence.restaurant.repository.mutator.RestaurantCuisinesMutator
import com.reservation.persistence.restaurant.repository.mutator.RestaurantNationalitiesMutator
import com.reservation.persistence.restaurant.repository.mutator.RestaurantPhotosMutator
import com.reservation.persistence.restaurant.repository.mutator.RestaurantTagMutator
import com.reservation.persistence.restaurant.repository.mutator.RestaurantWorkingDayMutator
import com.reservation.persistence.restaurant.repository.mutator.RestaurantWorkingDayMutator.WorkingDayMutatorForm
import com.reservation.restaurant.port.output.CreateRestaurant
import com.reservation.restaurant.port.output.CreateRestaurant.CreateProductInquiry
import org.springframework.stereotype.Component

@Component
class CreateRestaurantAdapter(
    private val jpaRepository: RestaurantJpaRepository,
) : CreateRestaurant {
    override fun command(inquiry: CreateProductInquiry): String {
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

        RestaurantWorkingDayMutator.appendWorkingDays(
            entity,
            inquiry.workingDays.map { WorkingDayMutatorForm(it.day, it.startTime, it.endTime) },
        )
        RestaurantPhotosMutator.appendPhoto(entity, inquiry.photos.map { it.url })
        RestaurantTagMutator.appendTag(entity, inquiry.tags)
        RestaurantNationalitiesMutator.appendNationality(entity, inquiry.nationalities)
        RestaurantCuisinesMutator.appendCuisine(entity, inquiry.cuisines)

        val result = jpaRepository.save(entity)
        return result.id!!
    }
}
