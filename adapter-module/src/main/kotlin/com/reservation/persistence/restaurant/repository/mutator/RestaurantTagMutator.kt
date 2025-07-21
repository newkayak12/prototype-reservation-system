package com.reservation.persistence.restaurant.repository.mutator

import com.reservation.persistence.restaurant.RestaurantEntity
import com.reservation.persistence.restaurant.RestaurantTagsEntity

object RestaurantTagMutator {
    private fun RestaurantEntity.removeTag(tagsId: Long) {
        val item = tagsAll().find { it.tagsId == tagsId }
        if (item == null) {
            return
        }
        adjustTag {
            remove(item)
        }
    }

    private fun RestaurantEntity.addTag(tagsId: Long) {
        val item = tagsAll().find { it.tagsId == tagsId }
        if (item != null) {
            return
        }

        adjustTag {
            add(RestaurantTagsEntity(this@addTag, tagsId))
        }
    }

    fun addTags(
        restaurantEntity: RestaurantEntity,
        tagsId: List<Long>,
    ) = tagsId.forEach {
        restaurantEntity.addTag(it)
    }

    fun purgeTags(restaurantEntity: RestaurantEntity) {
        restaurantEntity.adjustTag { removeAll(restaurantEntity.tagsAll()) }
    }
}
