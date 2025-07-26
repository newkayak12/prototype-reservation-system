package com.reservation.persistence.restaurant.repository.adapter

import com.reservation.persistence.restaurant.repository.jpa.RestaurantJpaRepository
import com.reservation.persistence.restaurant.repository.mutator.RestaurantNationalitiesMutator
import com.reservation.restaurant.port.output.ChangeRestaurant
import com.reservation.restaurant.port.output.ChangeRestaurant.ChangeRestaurantInquiry
import org.springframework.stereotype.Component

@Component
class ChangeRestaurantAdapter(
    private val jpaRepository: RestaurantJpaRepository,
) : ChangeRestaurant {
    override fun command(inquiry: ChangeRestaurantInquiry): Boolean {
        var result = false
        jpaRepository.findRestaurantEntityById(inquiry.id)
            .ifPresent {
                it.updateDescription(inquiry.name, inquiry.introduce)
                it.updateContact(inquiry.phone)
                it.updateAddress(
                    inquiry.zipCode,
                    inquiry.address,
                    inquiry.detail,
                    inquiry.latitude,
                    inquiry.longitude,
                )

                RestaurantNationalitiesMutator.adjustNationalities(
                    it,
                    inquiry.nationalities.map { nationality -> nationality.nationalitiesId },
                )
                result = true
            }

        return result
    }
}
