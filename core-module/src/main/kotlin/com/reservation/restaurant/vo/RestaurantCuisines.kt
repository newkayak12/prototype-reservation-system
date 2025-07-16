package com.reservation.restaurant.vo

data class RestaurantCuisines(
    private val cuisines: MutableList<Long> = mutableListOf(),
) {
    companion object {
        private const val MAX_CUISINE_SIZE = 10
        private const val MAX_CUISINE_SIZE_MESSAGE = "max cuisine size has been exceed!"
    }

    fun add(tagId: Long) {
        if (cuisines.contains(tagId)) return
        require(cuisines.size < MAX_CUISINE_SIZE) { MAX_CUISINE_SIZE_MESSAGE }
        cuisines.add(tagId)
    }

    fun remove(tagId: Long) {
        cuisines.remove(tagId)
    }

    fun allCuisines(): List<Long> = cuisines.toList()
}
