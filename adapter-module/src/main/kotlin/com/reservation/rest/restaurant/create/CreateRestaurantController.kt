package com.reservation.rest.restaurant.create

import com.reservation.authenticate.port.input.ExtractIdentifierFromHeaderQuery
import com.reservation.rest.common.response.BooleanResponse
import com.reservation.rest.restaurant.RestaurantUrl
import com.reservation.rest.restaurant.request.CreateRestaurantRequest
import com.reservation.restaurant.port.input.CreateRestaurantCommand
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
class CreateRestaurantController(
    val createRestaurantCommand: CreateRestaurantCommand,
    val extractIdentifierFromHeaderQuery: ExtractIdentifierFromHeaderQuery,
) {
    @PostMapping(RestaurantUrl.CREATE_RESTAURANT, consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun createRestaurant(
        header: HttpHeaders,
        @RequestPart(name = "request") @Valid request: CreateRestaurantRequest,
        @RequestPart(name = "photos") photos: List<MultipartFile> = listOf(),
    ): BooleanResponse {
        val identifier =
            extractIdentifierFromHeaderQuery
                .execute(header.getFirst(HttpHeaders.AUTHORIZATION))

        return BooleanResponse.created(
            createRestaurantCommand.execute(request.toCommand(identifier, photos)),
        )
    }
}
