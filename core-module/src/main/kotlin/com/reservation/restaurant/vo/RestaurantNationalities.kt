package com.reservation.restaurant.vo

data class RestaurantNationalities(
    private val nationalities: MutableList<Long> = mutableListOf(),
) {
    companion object {
        private const val MAX_NATIONALITY_SIZE = 10
        private const val MAX_NATIONALITY_SIZE_MESSAGE = "max nationality size has been exceed!"
    }

    fun add(tagId: Long) {
        if (nationalities.contains(tagId)) return
        require(nationalities.size < MAX_NATIONALITY_SIZE) { MAX_NATIONALITY_SIZE_MESSAGE }
        nationalities.add(tagId)
    }

    fun remove(tagId: Long) {
        nationalities.remove(tagId)
    }

    fun allNationalities(): List<Long> = nationalities.toList()
}
