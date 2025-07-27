package com.reservation.persistence.restaurant.repository.mutator

import com.reservation.persistence.restaurant.RestaurantEntity
import com.reservation.persistence.restaurant.RestaurantPhotoEntity

object RestaurantPhotosMutator {
    private fun RestaurantEntity.removePhoto(photoId: String) {
        val item = photosAll().find { it.id == photoId }
        if (item == null) {
            return
        }
        adjustPhotos {
            remove(item)
        }
    }

    private fun RestaurantEntity.removeExceptedPhotos(photoIds: List<String>) {
        val items = photosAll().filter { !photoIds.contains(it.url) }
        if (items.isEmpty()) {
            return
        }

        adjustPhotos {
            removeAll(items)
        }
    }

    private fun RestaurantEntity.addPhoto(url: String) {
        val item = photosAll().find { it.url == url }
        if (item != null) {
            return
        }

        adjustPhotos {
            add(RestaurantPhotoEntity(this@addPhoto, url))
        }
    }

    private fun RestaurantEntity.addPhotos(urls: List<String>) {
        val exists = photosAll().map { it.url }
        val items = urls.filter { !exists.contains(it) }
        if (items.isEmpty()) {
            return
        }

        adjustPhotos {
            for (element in items) {
                add(RestaurantPhotoEntity(this@addPhotos, element))
            }
        }
    }

    fun appendPhoto(
        restaurantEntity: RestaurantEntity,
        urls: List<String>,
    ) = urls.forEach {
        restaurantEntity.addPhoto(it)
    }

    fun purgeNationalities(restaurantEntity: RestaurantEntity) {
        restaurantEntity.adjustPhotos { removeAll(restaurantEntity.photosAll()) }
    }

    fun adjustNationalities(
        restaurantEntity: RestaurantEntity,
        ids: List<String>,
    ) {
        restaurantEntity.removeExceptedPhotos(ids)
        restaurantEntity.addPhotos(ids)
    }
}
