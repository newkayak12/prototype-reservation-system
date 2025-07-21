package com.reservation.restaurant.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.restaurant.port.input.UpdateRestaurantCommand
import com.reservation.restaurant.port.input.UpdateRestaurantCommand.ChangeRestaurantCommandDto
import com.reservation.restaurant.port.output.ChangeRestaurant
import com.reservation.restaurant.port.output.LoadRestaurant
import com.reservation.restaurant.service.ChangeRestaurantService

@UseCase
class ChangeRestaurantUseCase(
    private val changeRestaurantService: ChangeRestaurantService,
    private val loadRestaurant: LoadRestaurant,
    private val changeRestaurant: ChangeRestaurant,
) : UpdateRestaurantCommand {
    override fun execute(request: ChangeRestaurantCommandDto): Boolean {
        TODO("Not yet implemented")
    }
}
