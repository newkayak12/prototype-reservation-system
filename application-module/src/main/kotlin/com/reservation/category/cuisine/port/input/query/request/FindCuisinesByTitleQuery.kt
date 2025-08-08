package com.reservation.category.cuisine.port.input.query.request

import com.reservation.category.cuisine.port.output.FindCuisines.FindCuisinesInquiry

data class FindCuisinesByTitleQuery(
    val title: String?,
) {
    fun toInquiry() = FindCuisinesInquiry(null, title)
}
