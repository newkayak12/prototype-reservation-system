package com.reservation.redis.token.seller.template

import com.reservation.authenticate.port.output.SaveSellerUserRefreshToken
import com.reservation.authenticate.port.output.SaveSellerUserRefreshToken.SaveSellerUserRefreshTokenInquiry
import com.reservation.enumeration.Role
import com.reservation.redis.RedisKey
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class SaveGeneralUserRefreshTokenTemplate(
    val redisTemplate: RedisTemplate<String, String>,
) : SaveSellerUserRefreshToken {
    override fun command(inquiry: SaveSellerUserRefreshTokenInquiry) {
        redisTemplate
            .opsForValue()
            .set(
                "${RedisKey.REFRESH_TOKEN}:${Role.RESTAURANT_OWNER.name}:${inquiry.uuid}",
                inquiry.token,
                Duration.ofMillis(inquiry.ttl),
            )
    }
}
