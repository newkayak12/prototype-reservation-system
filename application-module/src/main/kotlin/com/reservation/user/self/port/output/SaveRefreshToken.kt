package com.reservation.user.self.port.output

interface SaveRefreshToken {
    fun command(inquiry: SaveRefreshTokenInquiry)

    data class SaveRefreshTokenInquiry(
        val uuid: String,
        val token: String,
        val ttl: Long,
    )
}
