package com.reservation.rest.resign.remove

import com.reservation.rest.common.response.BooleanResponse
import com.reservation.rest.resign.ResignUrl
import com.reservation.user.resign.port.input.ResignUserUseCase
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class ResignUserController(
    val resignUserUseCase: ResignUserUseCase,
) {
    @DeleteMapping(ResignUrl.RESIGN)
    fun resign(
        @PathVariable id: String,
    ): BooleanResponse = BooleanResponse.resetContents(resignUserUseCase.execute(id))
}
