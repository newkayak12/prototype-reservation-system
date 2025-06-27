package com.reservation.user.self.port.input

@FunctionalInterface
interface RefreshAccessTokenQuery {
    fun refresh(refreshToken: String): RefreshResult

    data class RefreshResult(val accessToken: String, val refreshToken: String)
}
