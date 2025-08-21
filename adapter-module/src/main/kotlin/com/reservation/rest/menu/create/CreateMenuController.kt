package com.reservation.rest.menu.create

import com.reservation.menu.port.input.CreateMenuUseCase
import com.reservation.rest.common.response.BooleanResponse
import com.reservation.rest.menu.MenuUrl
import com.reservation.rest.menu.request.CreateMenuRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
class CreateMenuController(
    val createMenuUseCase: CreateMenuUseCase,
) {
    @PostMapping(MenuUrl.CREATE, consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun createMenu(
        @RequestPart(name = "request") @Valid request: CreateMenuRequest,
        @RequestPart(name = "photos") photos: List<MultipartFile> = listOf(),
    ): BooleanResponse =
        BooleanResponse.created(createMenuUseCase.execute(request.toCommand(photos)))
}
