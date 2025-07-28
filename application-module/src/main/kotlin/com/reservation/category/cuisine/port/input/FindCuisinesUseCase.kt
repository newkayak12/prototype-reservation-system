package com.reservation.category.cuisine.port.input

import com.reservation.category.cuisine.port.input.query.request.FindCuisinesQuery
import com.reservation.category.cuisine.port.input.query.response.FindCuisinesQueryResult

interface FindCuisinesUseCase {
    fun execute(request: FindCuisinesQuery): List<FindCuisinesQueryResult>
}
