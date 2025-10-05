package com.reservation.persistence.restaurant.repository.mutator

import com.reservation.persistence.restaurant.entity.RestaurantEntity
import com.reservation.persistence.restaurant.entity.RestaurantTagsEntity

object RestaurantTagMutator {
    private fun RestaurantEntity.removeTag(tagId: Long) {
        val item = tagsAll().find { it.tagsId == tagId }
        if (item == null) {
            return
        }
        adjustTag {
            remove(item)
        }
    }

    private fun RestaurantEntity.removeExceptedTags(tagIds: List<Long>) {
        val items = tagsAll().filter { !tagIds.contains(it.tagsId) }
        if (items.isEmpty()) {
            return
        }

        adjustTag {
            removeAll(items)
        }
    }

    private fun RestaurantEntity.addTag(tagId: Long) {
        val item = tagsAll().find { it.tagsId == tagId }
        if (item != null) {
            return
        }

        adjustTag {
            add(RestaurantTagsEntity(this@addTag, tagId))
        }
    }

    private fun RestaurantEntity.addTags(tagIds: List<Long>) {
        val exists = tagsAll().map { it.tagsId }
        val items = tagIds.filter { !exists.contains(it) }

        if (items.isEmpty()) {
            return
        }

        adjustTag {
            for (element in items) {
                add(RestaurantTagsEntity(this@addTags, element))
            }
        }
    }

    fun appendTag(
        restaurantEntity: RestaurantEntity,
        tagsId: List<Long>,
    ) = tagsId.forEach {
        restaurantEntity.addTag(it)
    }

    fun purgeTags(restaurantEntity: RestaurantEntity) {
        restaurantEntity.adjustTag { removeAll(restaurantEntity.tagsAll()) }
    }

    fun adjustTags(
        restaurantEntity: RestaurantEntity,
        ids: List<Long>,
    ) {
        restaurantEntity.removeExceptedTags(ids)
        restaurantEntity.addTags(ids)
    }
}
