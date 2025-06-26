package com.reservation.redis.token.repository.adapter

import com.reservation.redis.token.repository.jpa.RefreshTokenRedisJpaRepository
import org.springframework.stereotype.Component

@Component
class RefreshTokenRedisAdapter(
    val refreshTokenRedisJpaRepository: RefreshTokenRedisJpaRepository,
)
