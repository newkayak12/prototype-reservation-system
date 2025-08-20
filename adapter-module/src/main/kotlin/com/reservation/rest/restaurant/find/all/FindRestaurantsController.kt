package com.reservation.rest.restaurant.find.all

import com.reservation.rest.common.response.PageResponse
import com.reservation.rest.restaurant.RestaurantUrl
import com.reservation.rest.restaurant.request.FindRestaurantsRequest
import com.reservation.rest.restaurant.response.FindRestaurantsResponses
import com.reservation.rest.restaurant.response.FindRestaurantsResponses.FindRestaurantsResponse
import com.reservation.restaurant.port.input.FindRestaurantsUseCase
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class FindRestaurantsController(
    private val findRestaurantsUseCase: FindRestaurantsUseCase,
) {
    @GetMapping(RestaurantUrl.FIND_A_RESTAURANTS)
    fun findRestaurants(
        @Valid request: FindRestaurantsRequest,
    ): PageResponse<FindRestaurantsResponse> {
        val result = findRestaurantsUseCase.execute(request.toQuery())
        val transformed = FindRestaurantsResponses.from(result)
        return PageResponse.ok(transformed.list, transformed.hasNext)
    }
}
