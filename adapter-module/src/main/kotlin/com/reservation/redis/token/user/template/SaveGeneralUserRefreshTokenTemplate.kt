package com.reservation.redis.token.user.template

import com.reservation.authenticate.port.output.SaveGeneralUserRefreshToken
import com.reservation.authenticate.port.output.SaveGeneralUserRefreshToken.SaveRefreshTokenInquiry
import com.reservation.enumeration.Role
import com.reservation.redis.RedisKey
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class SaveGeneralUserRefreshTokenTemplate(
    val redisTemplate: RedisTemplate<String, String>,
) : SaveGeneralUserRefreshToken {
    override fun command(inquiry: SaveRefreshTokenInquiry) {
        redisTemplate
            .opsForValue()
            .set(
                "${RedisKey.REFRESH_TOKEN}:${Role.USER.name}:${inquiry.uuid}",
                inquiry.token,
                Duration.ofMillis(inquiry.ttl),
            )
    }
}
