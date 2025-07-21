package com.reservation.persistence.restaurant.repository.mutator

import com.reservation.persistence.restaurant.RestaurantEntity
import com.reservation.persistence.restaurant.RestaurantNationalitiesEntity

object RestaurantNationalitiesMutator {
    private fun RestaurantEntity.removeNationality(nationalitiesId: Long) {
        val item = nationalitiesAll().find { it.nationalitiesId == nationalitiesId }
        if (item == null) {
            return
        }
        adjustNationalities {
            remove(item)
        }
    }

    private fun RestaurantEntity.removeNationalities(nationalitiesIds: List<Long>) {
        val items = nationalitiesAll().filter { nationalitiesIds.contains(it.nationalitiesId) }
        if (items == null || items.isEmpty()) {
            return
        }
        adjustNationalities {
            removeAll(items)
        }
    }

    private fun RestaurantEntity.addNationality(nationalitiesId: Long) {
        val item = nationalitiesAll().find { it.nationalitiesId == nationalitiesId }
        if (item != null) {
            return
        }

        adjustNationalities {
            add(RestaurantNationalitiesEntity(this@addNationality, nationalitiesId))
        }
    }

    private fun RestaurantEntity.addNationalities(nationalitiesId: List<Long>) {
        val exists = nationalitiesAll().map { it.nationalitiesId }
        val item: List<Long> = nationalitiesId.filter { !exists.contains(it) }

        if (item != null || item.isEmpty()) {
            return
        }

        adjustNationalities {
            for (element in item) {
                add(RestaurantNationalitiesEntity(this@addNationalities, element))
            }
        }
    }

    fun addNationality(
        restaurantEntity: RestaurantEntity,
        nationalities: List<Long>,
    ) = nationalities.forEach {
        restaurantEntity.addNationality(it)
    }

    fun purgeNationalities(restaurantEntity: RestaurantEntity) {
        restaurantEntity.adjustNationalities { removeAll(restaurantEntity.nationalitiesAll()) }
    }

    fun adjustNationalities(
        restaurantEntity: RestaurantEntity,
        ids: List<Long>,
    ) {
        restaurantEntity.removeNationalities(ids)
        restaurantEntity.addNationalities(ids)
    }
}
