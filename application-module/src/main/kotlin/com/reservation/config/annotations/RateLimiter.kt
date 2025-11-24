package com.reservation.config.annotations

import com.reservation.enumeration.RateLimitType
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.FUNCTION
import java.util.concurrent.TimeUnit

@Target(FUNCTION)
@Retention(RUNTIME)
@Suppress("LongParameterList")
annotation class RateLimiter(
    val key: String,
    val type: RateLimitType,
    val maximumWaitTime: Long,
    val maximumWaitTimeUnit: TimeUnit = TimeUnit.MINUTES,
    val rate: Long,
    val rateIntervalTime: Long,
    val rateIntervalTimeUnit: TimeUnit = TimeUnit.MINUTES,
    val bucketLiveTime: Long,
    val bucketLiveTimeUnit: TimeUnit = TimeUnit.MINUTES,
)
