package com.reservation.restaurant.vo

data class RestaurantTags(
    private val tags: MutableList<Long> = mutableListOf(),
) {
    companion object {
        private const val MAX_TAG_SIZE = 10
        private const val MAX_TAG_SIZE_MESSAGE = "max tag size has been exceed!"
    }

    fun add(tagId: Long) {
        if (tags.contains(tagId)) return
        require(tags.size < MAX_TAG_SIZE) { MAX_TAG_SIZE_MESSAGE }
        tags.add(tagId)
    }

    fun remove(tagId: Long) {
        tags.remove(tagId)
    }

    fun allTags(): List<Long> = tags.toList()
}
