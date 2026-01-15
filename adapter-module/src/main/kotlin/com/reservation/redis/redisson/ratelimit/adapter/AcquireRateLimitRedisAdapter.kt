package com.reservation.redis.redisson.ratelimit.adapter

import com.reservation.enumeration.RateLimitType
import com.reservation.enumeration.RateLimiterTemplateState
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.BucketLiveTimeSettings
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.MaximumWaitSettings
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.RateLimiterSettings
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.RateSettings
import com.reservation.redis.redisson.ratelimit.util.RateLimiterKeyGenerator
import org.redisson.api.RRateLimiter
import org.redisson.api.RateType
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class AcquireRateLimitRedisAdapter(
    private val redissonClient: RedissonClient,
) : AcquireRateLimiterTemplate {
    private var status = RateLimiterTemplateState.ACTIVATED

    override fun tryAcquire(
        rateLimiterSettings: RateLimiterSettings,
        maximumWaitSettings: MaximumWaitSettings,
        rateSettings: RateSettings,
        bucketLiveTimeSettings: BucketLiveTimeSettings,
    ): Boolean {
        val maximumWait = maximumWaitSettings.toDuration()

        val rateLimiter =
            getRateLimiter(
                rateLimiterSettings,
                rateSettings,
                bucketLiveTimeSettings,
            )

        return rateLimiter.tryAcquire(maximumWait)
    }

    override fun enable() {
        status = RateLimiterTemplateState.ACTIVATED
    }

    override fun disable() {
        status = RateLimiterTemplateState.DEACTIVATED
    }

    override fun status(): RateLimiterTemplateState {
        return status
    }

    private fun getRateLimiter(
        rateLimiterSettings: RateLimiterSettings,
        rateSettings: RateSettings,
        bucketLiveTimeSettings: BucketLiveTimeSettings,
    ): RRateLimiter {
        val name = rateLimiterSettings.key

        val type = rateLimiterSettings.type
        val rate = rateSettings.rate
        val rateIntervalTime = rateSettings.toDuration()
        val bucketLiveTime = bucketLiveTimeSettings.toDuration()

        return redissonClient.getRateLimiter(RateLimiterKeyGenerator.key(name)).apply {
            applySettings(type, rate, rateIntervalTime, bucketLiveTime)
        }
    }

    private fun RRateLimiter.applySettings(
        type: RateLimitType,
        rate: Long,
        rateInterval: Duration,
        bucketLiveTime: Duration,
    ): RRateLimiter {
        val rateType =
            when (type) {
                RateLimitType.WHOLE -> RateType.OVERALL
                RateLimitType.PER_CLIENT -> RateType.PER_CLIENT
            }

        this.trySetRate(rateType, rate, rateInterval, bucketLiveTime)
        return this
    }
}
