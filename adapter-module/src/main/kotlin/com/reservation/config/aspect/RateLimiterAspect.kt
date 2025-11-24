package com.reservation.config.aspect

import com.reservation.config.annotations.RateLimiter
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.BucketLiveTimeSettings
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.MaximumWaitSettings
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.RateLimiterSettings
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.RateSettings
import com.reservation.timetable.exception.TooManyCreateTimeTableOccupancyRequestException
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class RateLimiterAspect(
    private val spelParser: SpelParser,
    private val rateLimiterTemplate: AcquireRateLimiterTemplate,
) {
    private fun parseKey(
        proceedingJoinPoint: ProceedingJoinPoint,
        rateLimiter: RateLimiter,
    ): String {
        val expression = rateLimiter.key
        val method = (proceedingJoinPoint.signature as MethodSignature).method
        val args = proceedingJoinPoint.args
        return spelParser.parse(expression, method, args)
    }

    private fun acquireRateLimit(
        parseKey: String,
        rateLimiter: RateLimiter,
    ) {
        val rateLimiterSettings =
            RateLimiterSettings(
                key = parseKey,
                type = rateLimiter.type,
            )
        val maximumWaitSettings =
            MaximumWaitSettings(
                maximumWaitTime = rateLimiter.maximumWaitTime,
                maximumWaitTimeUnit = rateLimiter.maximumWaitTimeUnit,
            )
        val rateSettings =
            RateSettings(
                rate = rateLimiter.rate,
                rateIntervalTime = rateLimiter.rateIntervalTime,
                rateIntervalTimeUnit = rateLimiter.rateIntervalTimeUnit,
            )
        val bucketLiveTimeSettings =
            BucketLiveTimeSettings(
                bucketLiveTime = rateLimiter.bucketLiveTime,
                bucketLiveTimeUnit = rateLimiter.bucketLiveTimeUnit,
            )

        val isAcquired =
            rateLimiterTemplate.tryAcquire(
                rateLimiterSettings = rateLimiterSettings,
                maximumWaitSettings = maximumWaitSettings,
                rateSettings = rateSettings,
                bucketLiveTimeSettings = bucketLiveTimeSettings,
            )

        if (!isAcquired) throw TooManyCreateTimeTableOccupancyRequestException()
    }

    @Around("@annotation(rateLimiter)")
    fun executeDistributedLockAction(
        proceedingJoinPoint: ProceedingJoinPoint,
        rateLimiter: RateLimiter,
    ): Any? {
        val parsedKey = parseKey(proceedingJoinPoint, rateLimiter)
        acquireRateLimit(parsedKey, rateLimiter)

        return proceedingJoinPoint.proceed()
    }
}
