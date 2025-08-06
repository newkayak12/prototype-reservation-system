package com.reservation.rest.category.request

import com.reservation.category.cuisine.port.input.query.request.FindCuisinesByTitleQuery

data class FindCuisineRequest(val title: String?) {
    fun toQuery() = FindCuisinesByTitleQuery(title)
}
