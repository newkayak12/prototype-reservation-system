package com.reservation.rest.category.request

import com.reservation.category.cuisine.port.input.FindCuisinesQuery.FindCuisinesQueryDto

data class FindCuisineRequest(val title: String?) {
    fun toQuery() = FindCuisinesQueryDto(title)
}
