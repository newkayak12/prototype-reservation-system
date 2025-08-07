package com.reservation.restaurant.port.input

import com.reservation.restaurant.port.input.query.response.FindRestaurantQueryResult

interface FindRestaurantUseCase {
    fun execute(id: String): FindRestaurantQueryResult
}
