package com.reservation.redis.token.entity

import jakarta.persistence.Id
import org.springframework.data.redis.core.RedisHash

@RedisHash("RefreshTokenRedisEntity")
class RefreshTokenRedisEntity(
    uuid: String,
    refreshToken: String,
) {
    @Id
    val uuid: String = uuid
    var refreshToken: String = refreshToken

    fun changeRefreshToken(refreshToken: String) {
        this.refreshToken = refreshToken
    }
}
