package com.reservation.rest.user.general.sign

import com.reservation.rest.user.general.GeneralUserUrl
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class GeneralUserSignOutController {
    @PatchMapping(GeneralUserUrl.USER_SIGN_OUT)
    fun signOut(httpServletResponse: HttpServletResponse) {
        val refreshTokenCookie = Cookie(RefreshTokenDefinitions.REFRESH_TOKEN_KEY, null)

        refreshTokenCookie.path = RefreshTokenDefinitions.REFRESH_TOKEN_PATH
        refreshTokenCookie.secure = RefreshTokenDefinitions.SECURE
        refreshTokenCookie.isHttpOnly = RefreshTokenDefinitions.HTTP_ONLY
        refreshTokenCookie.maxAge = RefreshTokenDefinitions.FLUSH_AGE
        httpServletResponse.status = HttpStatus.NO_CONTENT.value()
        httpServletResponse.addCookie(refreshTokenCookie)
    }
}
