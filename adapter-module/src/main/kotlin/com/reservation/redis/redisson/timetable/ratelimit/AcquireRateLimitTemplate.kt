package com.reservation.redis.redisson.timetable.ratelimit

import com.reservation.timetable.port.output.AcquireTimeTableRateLimiter
import com.reservation.timetable.port.output.AcquireTimeTableRateLimiter.RateLimitSettings
import com.reservation.timetable.port.output.AcquireTimeTableRateLimiter.RateLimitType
import org.redisson.api.RRateLimiter
import org.redisson.api.RateType
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class AcquireRateLimitTemplate(
    private val redissonClient: RedissonClient,
) : AcquireTimeTableRateLimiter {
    override fun tryAcquire(
        name: String,
        duration: Duration,
        settings: RateLimitSettings,
    ): Boolean {
        val rateLimiter =
            RateLimiterStore.getOrCreateRateLimiter(name) {
                redissonClient.getRateLimiter(RateLimiterStore.key(name))
                    .apply { applySettings(settings) }
            }

        return rateLimiter.tryAcquire(duration)
    }

    private fun RRateLimiter.applySettings(settings: RateLimitSettings) {
        val rateType =
            when (settings.type) {
                RateLimitType.WHOLE -> RateType.OVERALL
                RateLimitType.PER_CLIENT -> RateType.PER_CLIENT
            }

        this.setRate(
            rateType,
            settings.rate,
            settings.rateInterval,
            settings.duration,
        )
    }
}
