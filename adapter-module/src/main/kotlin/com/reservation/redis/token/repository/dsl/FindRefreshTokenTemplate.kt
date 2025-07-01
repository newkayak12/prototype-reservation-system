package com.reservation.redis.token.repository.dsl

import com.reservation.redis.RedisKey
import com.reservation.user.self.port.output.FindRefreshToken
import com.reservation.user.self.port.output.FindRefreshToken.FindRefreshTokenInquiry
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

@Component
class FindRefreshTokenTemplate(
    val redisTemplate: RedisTemplate<String, String>,
) : FindRefreshToken {
    override fun query(inquiry: FindRefreshTokenInquiry): String? {
        return redisTemplate
            .opsForValue()
            .get("${RedisKey.REFRESH_TOKEN}:${inquiry.uuid}")
    }
}
