package com.reservation.category.cuisine.port.input.query.request

import com.reservation.category.cuisine.port.output.FindCuisines.FindCuisinesInquiry

data class FindCuisinesByIdsQuery(
    val ids: List<Long>?,
) {
    fun toInquiry() = FindCuisinesInquiry(ids, null)
}
