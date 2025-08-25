package com.reservation.rest.user.general.sign.refresh

import com.reservation.rest.user.RefreshTokenDefinitions
import com.reservation.rest.user.general.GeneralUserUrl
import com.reservation.rest.user.general.response.RefreshGeneralUserResponse
import com.reservation.user.self.port.input.RefreshGeneralUserAccessTokenUseCase
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class RefreshGeneralUserController(
    val refreshGeneralUserAccessTokenUseCase: RefreshGeneralUserAccessTokenUseCase,
) {
    private fun extractRefreshTokenFrom(cookies: Array<Cookie>): String =
        cookies.firstOrNull { it.name == RefreshTokenDefinitions.REFRESH_TOKEN_KEY }?.value ?: ""

    @GetMapping(GeneralUserUrl.REFRESH)
    fun refresh(
        httpServletRequest: HttpServletRequest,
        httpServletResponse: HttpServletResponse,
    ): RefreshGeneralUserResponse {
        val refreshToken = extractRefreshTokenFrom(httpServletRequest.cookies)
        val renewedToken = refreshGeneralUserAccessTokenUseCase.refresh(refreshToken)

        val refreshTokenCookie =
            Cookie(RefreshTokenDefinitions.REFRESH_TOKEN_KEY, renewedToken.refreshToken)
        refreshTokenCookie.path = RefreshTokenDefinitions.REFRESH_TOKEN_PATH
        refreshTokenCookie.secure = RefreshTokenDefinitions.SECURE
        refreshTokenCookie.isHttpOnly = RefreshTokenDefinitions.HTTP_ONLY
        httpServletResponse.addCookie(refreshTokenCookie)

        return RefreshGeneralUserResponse.from(renewedToken.accessToken)
    }
}
