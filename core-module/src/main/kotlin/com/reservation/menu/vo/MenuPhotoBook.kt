package com.reservation.menu.vo

data class MenuPhotoBook(
    private val photos: MutableList<MenuPhoto> = mutableListOf(),
) {
    companion object {
        private const val MAX_PHOTO_SIZE = 10
        private const val MAX_PHOTO_SIZE_MESSAGE = "max photo size has been exceed!"
    }

    fun add(photo: MenuPhoto) {
        if (photos.contains(photo)) return
        require(photos.size < MAX_PHOTO_SIZE) { MAX_PHOTO_SIZE_MESSAGE }
        photos.add(photo)
    }

    fun delete(photo: MenuPhoto) = photos.remove(photo)

    fun allPhotos() = photos.toList()
}
