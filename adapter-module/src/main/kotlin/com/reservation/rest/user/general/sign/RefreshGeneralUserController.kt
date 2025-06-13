package com.reservation.rest.user.general.sign

import com.reservation.rest.user.general.GeneralUserUrl
import com.reservation.rest.user.general.response.RefreshGeneralUserResponse
import com.reservation.user.self.port.input.RefreshAccessTokenQuery
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class RefreshGeneralUserController(
    val refreshAccessTokenQuery: RefreshAccessTokenQuery,
) {
    private fun extractRefreshTokenFrom(cookies: Array<Cookie>): String =
        cookies.firstOrNull { it.name == RefreshTokenDefinitions.REFRESH_TOKEN_KEY }?.value ?: ""

    @GetMapping(GeneralUserUrl.REFRESH)
    fun refresh(httpServletRequest: HttpServletRequest): RefreshGeneralUserResponse {
        val refreshToken = extractRefreshTokenFrom(httpServletRequest.cookies)
        return RefreshGeneralUserResponse.from(refreshAccessTokenQuery.refresh(refreshToken))
    }
}
