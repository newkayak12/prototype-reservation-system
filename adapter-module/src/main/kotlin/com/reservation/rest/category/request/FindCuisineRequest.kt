package com.reservation.rest.category.request

import com.reservation.category.cuisine.port.input.query.request.FindCuisinesQuery

data class FindCuisineRequest(val title: String?) {
    fun toQuery() = FindCuisinesQuery(title)
}
