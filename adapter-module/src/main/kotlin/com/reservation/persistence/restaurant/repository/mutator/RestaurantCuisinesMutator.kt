package com.reservation.persistence.restaurant.repository.mutator

import com.reservation.persistence.restaurant.RestaurantCuisinesEntity
import com.reservation.persistence.restaurant.RestaurantEntity

object RestaurantCuisinesMutator {
    private fun RestaurantEntity.removeCuisine(cuisinesId: Long) {
        val item = cuisinesAll().find { it.cuisinesId == cuisinesId }
        if (item == null) {
            return
        }
        adjustCuisines {
            remove(item)
        }
    }

    private fun RestaurantEntity.removeExceptedCuisines(cuisinesIds: List<Long>) {
        val items = cuisinesAll().filter { !cuisinesIds.contains(it.cuisinesId) }
        if (items.isEmpty()) {
            return
        }
        adjustCuisines {
            removeAll(items)
        }
    }

    private fun RestaurantEntity.addCuisine(cuisinesId: Long) {
        val item = cuisinesAll().find { it.cuisinesId == cuisinesId }
        if (item != null) {
            return
        }

        adjustCuisines {
            add(RestaurantCuisinesEntity(this@addCuisine, cuisinesId))
        }
    }

    private fun RestaurantEntity.addCuisines(cuisinesIds: List<Long>) {
        val exists = cuisinesAll().map { it.cuisinesId }
        val items = cuisinesIds.filter { !exists.contains(it) }
        if (items.isEmpty()) {
            return
        }

        adjustCuisines {
            for (element in items) {
                add(RestaurantCuisinesEntity(this@addCuisines, element))
            }
        }
    }

    fun appendCuisine(
        restaurantEntity: RestaurantEntity,
        cuisines: List<Long>,
    ) = cuisines.forEach {
        restaurantEntity.addCuisine(it)
    }

    fun purgeNationalities(restaurantEntity: RestaurantEntity) {
        restaurantEntity.adjustCuisines { removeAll(restaurantEntity.cuisinesAll()) }
    }

    fun adjustCuisines(
        restaurantEntity: RestaurantEntity,
        ids: List<Long>,
    ) {
        restaurantEntity.removeExceptedCuisines(ids)
        restaurantEntity.addCuisines(ids)
    }
}
