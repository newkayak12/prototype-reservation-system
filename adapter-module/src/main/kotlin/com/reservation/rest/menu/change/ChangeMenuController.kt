package com.reservation.rest.menu.change

import com.reservation.menu.port.input.ChangeMenuUseCase
import com.reservation.rest.common.response.BooleanResponse
import com.reservation.rest.menu.MenuUrl
import com.reservation.rest.menu.request.ChangeMenuRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
class ChangeMenuController(
    private val changeMenuUseCase: ChangeMenuUseCase,
) {
    @PostMapping(MenuUrl.CHANGE, consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun changeMenu(
        @PathVariable(name = "id") id: String,
        @RequestPart(name = "request") @Valid request: ChangeMenuRequest,
        @RequestPart(name = "photos") photos: List<MultipartFile> = listOf(),
    ): BooleanResponse =
        BooleanResponse.ok(changeMenuUseCase.execute(request.toCommand(id, photos)))
}
