package com.reservation.rest.restaurant.change

import com.reservation.authenticate.port.input.ExtractIdentifierFromHeaderUseCase
import com.reservation.rest.common.response.BooleanResponse
import com.reservation.rest.restaurant.RestaurantUrl
import com.reservation.rest.restaurant.request.ChangeRestaurantRequest
import com.reservation.restaurant.port.input.UpdateRestaurantUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
class ChangeRestaurantController(
    private val updateRestaurantUseCase: UpdateRestaurantUseCase,
    private val extractIdentifierFromHeaderUseCase: ExtractIdentifierFromHeaderUseCase,
) {
    @PutMapping(RestaurantUrl.CHANGE_RESTAURANT, consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun changeRestaurant(
        header: HttpHeaders,
        @PathVariable id: String,
        @RequestPart(name = "request") @Valid request: ChangeRestaurantRequest,
        @RequestPart(name = "photos") photos: List<MultipartFile> = listOf(),
    ): BooleanResponse {
        val identifier =
            extractIdentifierFromHeaderUseCase
                .execute(header.getFirst(HttpHeaders.AUTHORIZATION))

        return BooleanResponse.resetContents(
            updateRestaurantUseCase.execute(request.toCommand(id, identifier, photos)),
        )
    }
}
