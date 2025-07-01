package com.reservation.redis.token.user.repository.template

import com.reservation.enumeration.Role
import com.reservation.redis.RedisKey
import com.reservation.user.self.port.output.SaveGeneralUserRefreshToken
import com.reservation.user.self.port.output.SaveGeneralUserRefreshToken.SaveRefreshTokenInquiry
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class SaveGeneralUserRefreshTokenAdapter(
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
