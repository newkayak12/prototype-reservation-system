package com.reservation.user.self.port.output

interface FindGeneralUserRefreshToken {
    fun query(inquiry: FindRefreshTokenInquiry): String?

    data class FindRefreshTokenInquiry(val uuid: String)
}
