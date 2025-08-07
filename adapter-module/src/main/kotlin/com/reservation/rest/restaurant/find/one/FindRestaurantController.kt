package com.reservation.rest.restaurant.find.one

import com.reservation.rest.restaurant.RestaurantUrl
import com.reservation.rest.restaurant.response.FindRestaurantResponse
import com.reservation.restaurant.port.input.FindRestaurantUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class FindRestaurantController(
    private val findRestaurantUseCase: FindRestaurantUseCase,
) {
    @GetMapping(RestaurantUrl.FIND_A_RESTAURANT)
    fun findRestaurant(
        @PathVariable id: String,
    ): FindRestaurantResponse {
        return FindRestaurantResponse.from(findRestaurantUseCase.execute(id))
    }
}
