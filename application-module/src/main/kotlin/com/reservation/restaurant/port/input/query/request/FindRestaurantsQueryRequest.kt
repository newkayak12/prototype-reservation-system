package com.reservation.restaurant.port.input.query.request

import com.reservation.restaurant.port.output.FindRestaurants.FindRestaurantsInquiry

data class FindRestaurantsQueryRequest(
    val identifierFrom: String = "",
    val size: Long = 10,
    val searchText: String = "",
    val tags: List<Long> = listOf(),
    val nationalities: List<Long> = listOf(),
    val cuisines: List<Long> = listOf(),
) {
    fun toInquiry(): FindRestaurantsInquiry =
        FindRestaurantsInquiry(
            identifierFrom,
            size,
            searchText,
            tags,
            nationalities,
            cuisines,
        )
}
