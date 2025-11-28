package com.reservation.redis.redisson.ratelimit.adapter

import com.reservation.enumeration.RateLimitType
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.BucketLiveTimeSettings
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.MaximumWaitSettings
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.RateLimiterSettings
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.RateSettings
import com.reservation.redis.redisson.ratelimit.store.RateLimiterStore
import org.redisson.api.RRateLimiter
import org.redisson.api.RateType
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class AcquireRateLimitAdapter(
    private val redissonClient: RedissonClient,
) : AcquireRateLimiterTemplate {
    override fun tryAcquire(
        rateLimiterSettings: RateLimiterSettings,
        maximumWaitSettings: MaximumWaitSettings,
        rateSettings: RateSettings,
        bucketLiveTimeSettings: BucketLiveTimeSettings,
    ): Boolean {
        val name = rateLimiterSettings.key
        val maximumWait = maximumWaitSettings.toDuration()

        val type = rateLimiterSettings.type
        val rate = rateSettings.rate
        val rateIntervalTime = rateSettings.toDuration()
        val bucketLiveTime = bucketLiveTimeSettings.toDuration()

        val rateLimiter =
            RateLimiterStore.getOrCreateRateLimiter(name) {
                redissonClient.getRateLimiter(RateLimiterStore.key(name)).apply {
                    applySettings(
                        type,
                        rate,
                        rateIntervalTime,
                        bucketLiveTime,
                    )
                }
            }

        return rateLimiter.tryAcquire(maximumWait)
    }

    private fun RRateLimiter.applySettings(
        type: RateLimitType,
        rate: Long,
        rateInterval: Duration,
        bucketLiveTime: Duration,
    ) {
        val rateType =
            when (type) {
                RateLimitType.WHOLE -> RateType.OVERALL
                RateLimitType.PER_CLIENT -> RateType.PER_CLIENT
            }

        this.setRate(
            rateType,
            rate,
            rateInterval,
            bucketLiveTime,
        )
    }
}
