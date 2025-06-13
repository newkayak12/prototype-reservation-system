package com.reservation.user.self.port.input

interface RefreshAccessTokenQuery {
    fun refresh(refreshToken: String): String
}
