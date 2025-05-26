package com.reservation.rest.user.general

import com.reservation.rest.user.general.request.GeneralUserLoginRequest
import com.reservation.rest.user.general.response.GeneralUserLoginResponse
import com.reservation.user.self.port.input.AuthenticateGeneralUserQuery
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class UserController(
    val authenticateGeneralUserQuery: AuthenticateGeneralUserQuery,
) {
    @PutMapping(GeneralUserUrl.GENERAL_USER_LOGIN)
    fun login(
        @Valid @RequestBody request: GeneralUserLoginRequest,
    ): GeneralUserLoginResponse {
        return GeneralUserLoginResponse.from(
            authenticateGeneralUserQuery.execute(
                request.toQuery(),
            ),
        )
    }
}
