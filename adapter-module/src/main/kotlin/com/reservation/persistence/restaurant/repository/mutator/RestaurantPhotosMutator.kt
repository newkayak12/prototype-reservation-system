package com.reservation.persistence.restaurant.repository.mutator

import com.reservation.persistence.restaurant.RestaurantEntity
import com.reservation.persistence.restaurant.RestaurantPhotoEntity

object RestaurantPhotosMutator {
    private fun RestaurantEntity.removePhotos(photosId: String) {
        val item = photosAll().find { it.id == photosId }
        if (item == null) {
            return
        }
        adjustPhotos {
            remove(item)
        }
    }

    private fun RestaurantEntity.addPhotos(url: String) {
        val item = photosAll().find { it.url == url }
        if (item != null) {
            return
        }

        adjustPhotos {
            add(RestaurantPhotoEntity(this@addPhotos, url))
        }
    }

    fun addPhotos(
        restaurantEntity: RestaurantEntity,
        urls: List<String>,
    ) = urls.forEach {
        restaurantEntity.addPhotos(it)
    }

    fun purgeNationalities(restaurantEntity: RestaurantEntity) {
        restaurantEntity.adjustTag { removeAll(restaurantEntity.tagsAll()) }
    }
}
