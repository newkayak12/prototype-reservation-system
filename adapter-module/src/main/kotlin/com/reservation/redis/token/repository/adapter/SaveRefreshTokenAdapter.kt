package com.reservation.redis.token.repository.adapter

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

@Component
class SaveRefreshTokenAdapter(
    val redisTemplate: RedisTemplate<String, String>,
)
