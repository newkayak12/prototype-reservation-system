package com.reservation.authenticate.port.output

fun interface SaveSellerUserRefreshToken {
    fun command(inquiry: SaveSellerUserRefreshTokenInquiry)

    data class SaveSellerUserRefreshTokenInquiry(
        val uuid: String,
        val token: String,
        val ttl: Long,
    )
}
