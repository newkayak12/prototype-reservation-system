package com.reservation.config.aspect

import com.reservation.config.annotations.RateLimiter
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.BucketLiveTimeSettings
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.MaximumWaitSettings
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.RateLimiterSettings
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.RateSettings
import com.reservation.redis.redisson.ratelimit.adapter.AcquireRateLimitInMemoryAdapter
import com.reservation.redis.redisson.ratelimit.adapter.AcquireRateLimitRedisAdapter
import com.reservation.timetable.exceptions.TooManyCreateTimeTableOccupancyRequestException
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
    private val acquireRateLimitInMemoryAdapter: AcquireRateLimitInMemoryAdapter,
    private val acquireRateLimitRedisAdapter: AcquireRateLimitRedisAdapter,
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

    private fun isRedisEnabled(): Boolean {
        val status = acquireRateLimitRedisAdapter.status()
        return status.isActivated()
    }

    private fun giveMeRateLimiterTemplate(): AcquireRateLimiterTemplate {
        if (isRedisEnabled()) {
            return acquireRateLimitRedisAdapter
        }

        return acquireRateLimitInMemoryAdapter
    }

    private fun giveMeSettings(
        parseKey: String,
        rateLimiter: RateLimiter,
    ) = RateLimiterSettings(
        key = parseKey,
        type = rateLimiter.type,
    )

    private fun giveMeMaximumWaitSettings(rateLimiter: RateLimiter) =
        MaximumWaitSettings(
            maximumWaitTime = rateLimiter.maximumWaitTime,
            maximumWaitTimeUnit = rateLimiter.maximumWaitTimeUnit,
        )

    private fun giveMeRateSettings(rateLimiter: RateLimiter) =
        RateSettings(
            rate = rateLimiter.rate,
            rateIntervalTime = rateLimiter.rateIntervalTime,
            rateIntervalTimeUnit = rateLimiter.rateIntervalTimeUnit,
        )

    private fun giveMeBucketLiveSettings(rateLimiter: RateLimiter) =
        BucketLiveTimeSettings(
            bucketLiveTime = rateLimiter.bucketLiveTime,
            bucketLiveTimeUnit = rateLimiter.bucketLiveTimeUnit,
        )

    private fun acquireRateLimit(
        parseKey: String,
        rateLimiter: RateLimiter,
    ) {
        val rateLimiterSettings = giveMeSettings(parseKey, rateLimiter)
        val maximumWaitSettings = giveMeMaximumWaitSettings(rateLimiter)
        val rateSettings = giveMeRateSettings(rateLimiter)
        val bucketLiveTimeSettings = giveMeBucketLiveSettings(rateLimiter)

        val isAcquired =
            giveMeRateLimiterTemplate()
                .tryAcquire(
                    rateLimiterSettings = rateLimiterSettings,
                    maximumWaitSettings = maximumWaitSettings,
                    rateSettings = rateSettings,
                    bucketLiveTimeSettings = bucketLiveTimeSettings,
                )

        if (!isAcquired) throw TooManyCreateTimeTableOccupancyRequestException()

        if (isRedisEnabled()) {
            acquireRateLimitInMemoryAdapter.syncAcquiredResult(
                rateLimiterSettings,
                maximumWaitSettings,
                rateSettings,
                bucketLiveTimeSettings,
            )
        }
    }

    @Around("@annotation(com.reservation.config.annotations.RateLimiter)")
    @Suppress("UseCheckOrError", "RethrowCaughtException")
    fun executeDistributedLockAction(proceedingJoinPoint: ProceedingJoinPoint): Any? {
        val method = (proceedingJoinPoint.signature as MethodSignature).method
        val targetClass = proceedingJoinPoint.target::class.java
        val targetMethod = targetClass.getMethod(method.name, *method.parameterTypes)
        val rateLimiter =
            targetMethod.getAnnotation(RateLimiter::class.java)
                ?: method.getAnnotation(RateLimiter::class.java)
                ?: throw IllegalStateException(
                    "@RateLimiter annotation not found on method ${method.name}",
                )

        val parsedKey = parseKey(proceedingJoinPoint, rateLimiter)
        acquireRateLimit(parsedKey, rateLimiter)

        return proceedingJoinPoint.proceed()
    }
}
