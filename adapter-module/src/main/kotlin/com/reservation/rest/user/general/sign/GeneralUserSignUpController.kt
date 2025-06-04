package com.reservation.rest.user.general.sign

import com.reservation.rest.common.response.BooleanResponse
import com.reservation.rest.user.general.GeneralUserUrl
import com.reservation.rest.user.general.request.GeneralUserSignUpRequest
import com.reservation.user.self.port.input.CreateGeneralUserCommand
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class GeneralUserSignUpController(
    val createGeneralUserCommand: CreateGeneralUserCommand,
) {
    @PostMapping(GeneralUserUrl.USER_SIGN_UP)
    fun signUp(
        @Valid @RequestBody request: GeneralUserSignUpRequest,
    ): BooleanResponse =
        BooleanResponse.created(createGeneralUserCommand.execute(request.toCommand()))
}
