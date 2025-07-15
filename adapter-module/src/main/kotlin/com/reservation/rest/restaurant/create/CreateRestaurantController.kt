package com.reservation.rest.restaurant.create

import com.reservation.authenticate.port.input.ExtractIdentifierFromHeaderQuery
import com.reservation.rest.common.response.BooleanResponse
import com.reservation.rest.restaurant.RestaurantUrl
import com.reservation.rest.restaurant.request.CreateRestaurantRequest
import com.reservation.restaurant.port.input.CreateRestaurantCommand
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class CreateRestaurantController(
    val createRestaurantCommand: CreateRestaurantCommand,
    val extractIdentifierFromHeaderQuery: ExtractIdentifierFromHeaderQuery,
) {
    @PostMapping(RestaurantUrl.CREATE_RESTAURANT)
    fun createRestaurant(
        header: HttpHeaders,
        @RequestBody @Valid request: CreateRestaurantRequest,
    ): BooleanResponse {
        val identifier =
            extractIdentifierFromHeaderQuery
                .execute(header.getFirst(HttpHeaders.AUTHORIZATION))

        return BooleanResponse.ok(
            createRestaurantCommand.execute(request.toCommand(identifier)),
        )
    }
}
