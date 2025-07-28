package com.reservation.restaurant.port.input

import com.reservation.restaurant.port.input.command.request.ChangeRestaurantCommand

interface UpdateRestaurantUseCase {
    fun execute(request: ChangeRestaurantCommand): Boolean
}
