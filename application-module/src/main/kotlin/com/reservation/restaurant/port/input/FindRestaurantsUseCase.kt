package com.reservation.restaurant.port.input

import com.reservation.restaurant.port.input.query.request.FindRestaurantsQueryRequest
import com.reservation.restaurant.port.input.query.response.FindRestaurantsQueryResults

interface FindRestaurantsUseCase {
    fun execute(query: FindRestaurantsQueryRequest): FindRestaurantsQueryResults
}
