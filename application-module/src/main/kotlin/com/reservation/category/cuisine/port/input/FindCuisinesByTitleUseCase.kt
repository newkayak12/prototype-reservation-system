package com.reservation.category.cuisine.port.input

import com.reservation.category.cuisine.port.input.query.request.FindCuisinesByTitleQuery
import com.reservation.category.cuisine.port.input.query.response.FindCuisinesQueryResult

interface FindCuisinesByTitleUseCase {
    fun execute(request: FindCuisinesByTitleQuery): List<FindCuisinesQueryResult>
}
