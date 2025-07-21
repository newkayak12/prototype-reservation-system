package com.reservation.persistence.restaurant.repository.mutator

import com.reservation.persistence.restaurant.RestaurantCuisinesEntity
import com.reservation.persistence.restaurant.RestaurantEntity

object RestaurantCuisinesMutator {
    private fun RestaurantEntity.removeCuisines(cuisinesId: Long) {
        val item = cuisinesAll().find { it.cuisinesId == cuisinesId }
        if (item == null) {
            return
        }
        adjustCuisines {
            remove(item)
        }
    }

    private fun RestaurantEntity.addCuisines(cuisinesId: Long) {
        val item = cuisinesAll().find { it.cuisinesId == cuisinesId }
        if (item != null) {
            return
        }

        adjustCuisines {
            add(RestaurantCuisinesEntity(this@addCuisines, cuisinesId))
        }
    }

    fun addCuisines(
        restaurantEntity: RestaurantEntity,
        cuisines: List<Long>,
    ) = cuisines.forEach {
        restaurantEntity.addCuisines(it)
    }

    fun purgeNationalities(restaurantEntity: RestaurantEntity) {
        restaurantEntity.adjustTag { removeAll(restaurantEntity.tagsAll()) }
    }
}
