package com.reservation.redis.redisson.ratelimit.util

import com.reservation.redis.config.RedissonNameSpace.RATE_LIMITER_NAMESPACE

object RateLimiterKeyGenerator {
    fun key(name: String) = "$RATE_LIMITER_NAMESPACE$name"
}
