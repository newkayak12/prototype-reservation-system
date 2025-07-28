package com.reservation.rest.restaurant.create

import com.reservation.authenticate.port.input.ExtractIdentifierFromHeaderUseCase
import com.reservation.rest.common.response.BooleanResponse
import com.reservation.rest.restaurant.RestaurantUrl
import com.reservation.rest.restaurant.request.CreateRestaurantRequest
import com.reservation.restaurant.port.input.CreateRestaurantUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
class CreateRestaurantController(
    private val createRestaurantUseCase: CreateRestaurantUseCase,
    private val extractIdentifierFromHeaderUseCase: ExtractIdentifierFromHeaderUseCase,
) {
    @PostMapping(RestaurantUrl.CREATE_RESTAURANT, consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun createRestaurant(
        header: HttpHeaders,
        @RequestPart(name = "request") @Valid request: CreateRestaurantRequest,
        @RequestPart(name = "photos") photos: List<MultipartFile> = listOf(),
    ): BooleanResponse {
        val identifier =
            extractIdentifierFromHeaderUseCase
                .execute(header.getFirst(HttpHeaders.AUTHORIZATION))

        return BooleanResponse.created(
            createRestaurantUseCase.execute(request.toCommand(identifier, photos)),
        )
    }
}
