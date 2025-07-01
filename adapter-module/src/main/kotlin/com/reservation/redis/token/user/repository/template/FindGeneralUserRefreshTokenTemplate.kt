package com.reservation.redis.token.user.repository.template

import com.reservation.enumeration.Role
import com.reservation.redis.RedisKey
import com.reservation.user.self.port.output.FindGeneralUserRefreshToken
import com.reservation.user.self.port.output.FindGeneralUserRefreshToken.FindRefreshTokenInquiry
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

@Component
class FindGeneralUserRefreshTokenTemplate(
    val redisTemplate: RedisTemplate<String, String>,
) : FindGeneralUserRefreshToken {
    override fun query(inquiry: FindRefreshTokenInquiry): String? {
        return redisTemplate
            .opsForValue()
            .get("${RedisKey.REFRESH_TOKEN}:${Role.USER.name}:${inquiry.uuid}")
    }
}
