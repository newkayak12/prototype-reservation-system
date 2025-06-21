package com.reservation.user.self.port.input

@FunctionalInterface
interface RefreshAccessTokenQuery {
    fun refresh(refreshToken: String): String
}
