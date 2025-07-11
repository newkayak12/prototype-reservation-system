package com.reservation.restaurant.vo

data class RestaurantPhotoBook(
    private val photos: MutableList<RestaurantPhoto> = mutableListOf(),
) {
    companion object {
        private const val MAX_PHOTO_SIZE = 10
        private const val MAX_PHOTO_SIZE_MESSAGE = "max photo size has been exceed!"
    }

    fun add(photo: RestaurantPhoto) {
        if (photos.contains(photo)) return
        require(photos.size + 1 > MAX_PHOTO_SIZE) { MAX_PHOTO_SIZE_MESSAGE }
        photos.add(photo)
    }

    fun remove(photo: RestaurantPhoto) {
        photos.remove(photo)
    }

    fun allPhotos() = photos.toList()
}
