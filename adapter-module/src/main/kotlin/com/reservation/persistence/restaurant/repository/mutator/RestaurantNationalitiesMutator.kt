package com.reservation.persistence.restaurant.repository.mutator

import com.reservation.persistence.restaurant.RestaurantEntity
import com.reservation.persistence.restaurant.RestaurantNationalitiesEntity

object RestaurantNationalitiesMutator {
    private fun RestaurantEntity.removeNationalities(nationalitiesId: Long) {
        val item = nationalitiesAll().find { it.nationalitiesId == nationalitiesId }
        if (item == null) {
            return
        }
        adjustNationalities {
            remove(item)
        }
    }

    private fun RestaurantEntity.addNationalities(nationalitiesId: Long) {
        val item = nationalitiesAll().find { it.nationalitiesId == nationalitiesId }
        if (item != null) {
            return
        }

        adjustNationalities {
            add(RestaurantNationalitiesEntity(this@addNationalities, nationalitiesId))
        }
    }

    fun addNationalities(
        restaurantEntity: RestaurantEntity,
        nationalities: List<Long>,
    ) = nationalities.forEach {
        restaurantEntity.addNationalities(it)
    }

    fun purgeNationalities(restaurantEntity: RestaurantEntity) {
        restaurantEntity.adjustNationalities { removeAll(restaurantEntity.nationalitiesAll()) }
    }
}
