package com.reservation.redis.redisson.ratelimit.model

import com.reservation.enumeration.RateLimitType
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

class InMemoryRateLimiter(
    val key: String,
    val type: RateLimitType,
    val rate: Long,
    val rateIntervalTime: Duration,
    val bucketLiveTime: Duration,
    val maximumWait: Duration,
    private val availablePermits: AtomicLong,
) {
    companion object {
        private const val RATE_LIMITER_LIMIT_ZERO = 0L
        private const val RATE_LIMITER_CONSUME_COUNT_ONE = 1L
    }

    fun tryAcquire(): Boolean {
        while (true) {
            val nowPermits = availablePermits.get()

            if (nowPermits - RATE_LIMITER_CONSUME_COUNT_ONE < RATE_LIMITER_LIMIT_ZERO) return false

            val calculateResult =
                availablePermits.compareAndSet(
                    nowPermits,
                    nowPermits - RATE_LIMITER_CONSUME_COUNT_ONE,
                )

            if (calculateResult) {
                return true
            }
        }
    }

    fun acquire() {
        availablePermits.getAndUpdate {
            if (it - 1 < 0) {
                it
            } else {
                it - 1
            }
        }
    }

    fun availablePermits(): Long = availablePermits.get()
}
