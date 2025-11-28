package com.reservation.redis.redisson.ratelimit

import com.reservation.enumeration.RateLimitType
import java.time.Duration
import java.util.concurrent.TimeUnit

interface AcquireRateLimiterTemplate {
    fun tryAcquire(
        rateLimiterSettings: RateLimiterSettings,
        maximumWaitSettings: MaximumWaitSettings,
        rateSettings: RateSettings,
        bucketLiveTimeSettings: BucketLiveTimeSettings,
    ): Boolean

    data class RateLimiterSettings(
        val key: String,
        val type: RateLimitType,
    )

    data class MaximumWaitSettings(
        val maximumWaitTime: Long,
        val maximumWaitTimeUnit: TimeUnit,
    ) {
        fun toDuration(): Duration =
            Duration.of(maximumWaitTime, maximumWaitTimeUnit.toChronoUnit())
    }

    data class RateSettings(
        val rate: Long,
        val rateIntervalTime: Long,
        val rateIntervalTimeUnit: TimeUnit,
    ) {
        fun toDuration(): Duration =
            Duration.of(rateIntervalTime, rateIntervalTimeUnit.toChronoUnit())
    }

    data class BucketLiveTimeSettings(
        val bucketLiveTime: Long,
        val bucketLiveTimeUnit: TimeUnit,
    ) {
        fun toDuration(): Duration = Duration.of(bucketLiveTime, bucketLiveTimeUnit.toChronoUnit())
    }
}
