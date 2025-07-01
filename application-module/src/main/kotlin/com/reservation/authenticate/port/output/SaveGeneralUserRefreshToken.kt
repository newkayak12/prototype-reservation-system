package com.reservation.authenticate.port.output

interface SaveGeneralUserRefreshToken {
    fun command(inquiry: SaveRefreshTokenInquiry)

    data class SaveRefreshTokenInquiry(
        val uuid: String,
        val token: String,
        val ttl: Long,
    )
}
