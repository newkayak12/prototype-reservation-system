package com.reservation.redis.redisson.ratelimit.adapter

import com.reservation.enumeration.RateLimiterTemplateState
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.BucketLiveTimeSettings
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.MaximumWaitSettings
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.RateLimiterSettings
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.RateSettings
import com.reservation.redis.redisson.ratelimit.model.InMemoryRateLimiter
import com.reservation.redis.redisson.ratelimit.util.RateLimiterKeyGenerator
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Component
class AcquireRateLimitInMemoryAdapter : AcquireRateLimiterTemplate {
    private val store = ConcurrentHashMap<String, InMemoryRateLimiter>()

    override fun tryAcquire(
        rateLimiterSettings: RateLimiterSettings,
        maximumWaitSettings: MaximumWaitSettings,
        rateSettings: RateSettings,
        bucketLiveTimeSettings: BucketLiveTimeSettings,
    ): Boolean {
        val rateLimiter =
            getRateLimiter(
                rateLimiterSettings,
                maximumWaitSettings,
                rateSettings,
                bucketLiveTimeSettings,
            )

        return rateLimiter.tryAcquire()
    }

    override fun enable() {
        TODO("Not Plan to implement")
    }

    override fun disable() {
        TODO("Not Plan to implement")
    }

    override fun status(): RateLimiterTemplateState = RateLimiterTemplateState.ACTIVATED

    private fun getRateLimiter(
        rateLimiterSettings: RateLimiterSettings,
        maximumWaitSettings: MaximumWaitSettings,
        rateSettings: RateSettings,
        bucketLiveTimeSettings: BucketLiveTimeSettings,
    ): InMemoryRateLimiter {
        val name = rateLimiterSettings.key
        val key = RateLimiterKeyGenerator.key(name)
        val type = rateLimiterSettings.type
        val rate = rateSettings.rate
        val rateIntervalTime = rateSettings.toDuration()
        val bucketLiveTime = bucketLiveTimeSettings.toDuration()
        val maximumWait = maximumWaitSettings.toDuration()

        val inMemoryRateLimiter =
            InMemoryRateLimiter(
                key,
                type,
                rate,
                rateIntervalTime,
                bucketLiveTime,
                maximumWait,
                AtomicLong(rate),
            )

        return store.putIfAbsent(key, inMemoryRateLimiter) ?: inMemoryRateLimiter
    }

    fun syncAcquiredResult(
        rateLimiterSettings: RateLimiterSettings,
        maximumWaitSettings: MaximumWaitSettings,
        rateSettings: RateSettings,
        bucketLiveTimeSettings: BucketLiveTimeSettings,
    ) {
        val rateLimiter =
            getRateLimiter(
                rateLimiterSettings,
                maximumWaitSettings,
                rateSettings,
                bucketLiveTimeSettings,
            )

        rateLimiter.acquire()
    }
}
