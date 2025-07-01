package com.reservation.rest.user.seller.sign

import com.reservation.authenticate.port.input.AuthenticateSellerUserQuery
import com.reservation.rest.user.general.RefreshTokenDefinitions
import com.reservation.rest.user.seller.SellerUserUrl
import com.reservation.rest.user.seller.request.SellerUserLoginRequest
import com.reservation.rest.user.seller.response.LoginSellerUserResponse
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class SellerUserSignInController(
    private val authenticateSellerUserQuery: AuthenticateSellerUserQuery,
) {
    @PutMapping(SellerUserUrl.USER_SIGN_IN)
    fun signIn(
        @RequestBody @Valid request: SellerUserLoginRequest,
        httpServletResponse: HttpServletResponse,
    ): LoginSellerUserResponse {
        val result = authenticateSellerUserQuery.execute(request.toQuery())

        val refreshTokenCookie =
            Cookie(RefreshTokenDefinitions.REFRESH_TOKEN_KEY, result.refreshToken)
        refreshTokenCookie.path = RefreshTokenDefinitions.REFRESH_TOKEN_PATH
        refreshTokenCookie.secure = RefreshTokenDefinitions.SECURE
        refreshTokenCookie.isHttpOnly = RefreshTokenDefinitions.HTTP_ONLY
        httpServletResponse.addCookie(refreshTokenCookie)

        return LoginSellerUserResponse.from(result)
    }
}
