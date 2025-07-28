package com.reservation.restaurant.port.input

import com.reservation.restaurant.port.input.command.request.CreateProductCommand

interface CreateRestaurantUseCase {
    /**
     * 성공하면 true, 실패하면 false
     */
    fun execute(request: CreateProductCommand): Boolean
}
