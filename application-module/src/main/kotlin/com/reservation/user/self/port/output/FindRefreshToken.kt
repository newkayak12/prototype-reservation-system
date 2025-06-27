package com.reservation.user.self.port.output

interface FindRefreshToken {
    fun query(inquiry: FindRefreshTokenInquiry): String?

    data class FindRefreshTokenInquiry(val uuid: String)
}
