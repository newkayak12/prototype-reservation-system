package com.reservation.aspects.ratelimiter

import com.reservation.config.annotations.RateLimiter
import com.reservation.enumeration.RateLimitType.WHOLE
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit.HOURS
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS

@Component
@Profile("test")
class RateLimiterAspectTestComponent {
    @RateLimiter(
        key = "test-acquire-rate-limit-key",
        type = WHOLE,
        maximumWaitTime = 15,
        maximumWaitTimeUnit = SECONDS,
        rate = 100,
        rateIntervalTime = 1,
        rateIntervalTimeUnit = MINUTES,
        bucketLiveTime = 1,
        bucketLiveTimeUnit = HOURS,
    )
    fun rateLimiterTest(command: String): String = command
}
