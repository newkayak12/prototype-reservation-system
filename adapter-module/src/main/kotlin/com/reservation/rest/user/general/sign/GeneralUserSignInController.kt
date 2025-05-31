package com.reservation.rest.user.general.sign

import com.reservation.rest.user.general.GeneralUserUrl
import com.reservation.rest.user.general.request.GeneralUserLoginRequest
import com.reservation.rest.user.general.response.GeneralUserLoginResponse
import com.reservation.user.self.port.input.AuthenticateGeneralUserQuery
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class GeneralUserSignInController(
    val authenticateGeneralUserQuery: AuthenticateGeneralUserQuery,
) {
    @PutMapping(GeneralUserUrl.GENERAL_USER_SIGN_IN)
    fun signIn(
        @Valid @RequestBody request: GeneralUserLoginRequest,
        httpServletResponse: HttpServletResponse,
    ): GeneralUserLoginResponse {
        val result = authenticateGeneralUserQuery.execute(request.toQuery())

        val refreshTokenCookie =
            Cookie(RefreshTokenDefinitions.REFRESH_TOKEN_KEY, result.refreshToken)
        refreshTokenCookie.path = RefreshTokenDefinitions.REFRESH_TOKEN_PATH
        refreshTokenCookie.secure = RefreshTokenDefinitions.SECURE
        refreshTokenCookie.isHttpOnly = RefreshTokenDefinitions.HTTP_ONLY
        httpServletResponse.addCookie(refreshTokenCookie)

        return GeneralUserLoginResponse.from(result)
    }
}
