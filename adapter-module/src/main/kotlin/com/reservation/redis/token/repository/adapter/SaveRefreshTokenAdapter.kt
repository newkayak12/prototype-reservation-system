package com.reservation.redis.token.repository.adapter

import com.reservation.redis.RedisKey
import com.reservation.user.self.port.output.SaveRefreshToken
import com.reservation.user.self.port.output.SaveRefreshToken.SaveRefreshTokenInquiry
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class SaveRefreshTokenAdapter(
    val redisTemplate: RedisTemplate<String, String>,
) : SaveRefreshToken {
    override fun command(inquiry: SaveRefreshTokenInquiry) {
        redisTemplate
            .opsForValue()
            .set(
                "${RedisKey.REFRESH_TOKEN}:${inquiry.uuid}",
                inquiry.token,
                Duration.ofMillis(inquiry.ttl),
            )
    }
}
