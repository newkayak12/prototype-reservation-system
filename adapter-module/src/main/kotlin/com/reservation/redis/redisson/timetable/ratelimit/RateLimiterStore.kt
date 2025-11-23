package com.reservation.redis.redisson.timetable.ratelimit

import com.reservation.redis.config.RedissonNameSpace.RATE_LIMITER_NAMESPACE
import org.redisson.api.RRateLimiter

object RateLimiterStore {
    private val RATE_LIMITER: ThreadLocal<MutableMap<String, RRateLimiter>> =
        ThreadLocal.withInitial {
            mutableMapOf()
        }

    fun key(name: String) = "$RATE_LIMITER_NAMESPACE$name"

    fun getOrCreateRateLimiter(
        name: String,
        rateLimiterProvider: () -> RRateLimiter,
    ): RRateLimiter = RATE_LIMITER.get().computeIfAbsent(key(name)) { rateLimiterProvider() }
}
