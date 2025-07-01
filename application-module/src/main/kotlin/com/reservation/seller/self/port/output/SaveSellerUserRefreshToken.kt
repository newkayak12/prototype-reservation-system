package com.reservation.seller.self.port.output

interface SaveSellerUserRefreshToken {
    fun command(inquiry: SaveSellerUserRefreshTokenInquiry)

    data class SaveSellerUserRefreshTokenInquiry(
        val uuid: String,
        val token: String,
        val ttl: Long,
    )
}
