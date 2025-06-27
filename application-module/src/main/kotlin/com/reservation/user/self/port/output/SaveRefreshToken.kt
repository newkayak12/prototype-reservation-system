package com.reservation.user.self.port.output

interface SaveRefreshToken {
    fun command(
        token: String,
        ttl: Long,
    )
}
