package com.reservation.rest.restaurant.request

import com.reservation.restaurant.port.input.query.request.FindRestaurantsQueryRequest
import jakarta.validation.constraints.Min

data class FindRestaurantsRequest(
    val identifierFrom: String = "",
    @field:Min(value = 1L)
    val size: Long = 10,
    val searchText: String = "",
    val tags: List<Long> = listOf(),
    val nationalities: List<Long> = listOf(),
    val cuisines: List<Long> = listOf(),
) {
    fun toQuery() =
        FindRestaurantsQueryRequest(
            identifierFrom,
            size,
            searchText,
            tags,
            nationalities,
            cuisines,
        )
}
