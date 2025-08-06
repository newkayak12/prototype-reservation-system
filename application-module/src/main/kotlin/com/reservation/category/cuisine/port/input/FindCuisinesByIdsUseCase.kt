package com.reservation.category.cuisine.port.input

import com.reservation.category.cuisine.port.input.query.request.FindCuisinesByIdsQuery
import com.reservation.category.cuisine.port.input.query.response.FindCuisinesQueryResult

interface FindCuisinesByIdsUseCase {
    fun execute(request: FindCuisinesByIdsQuery): List<FindCuisinesQueryResult>
}
