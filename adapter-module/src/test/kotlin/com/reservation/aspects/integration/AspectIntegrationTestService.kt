package com.reservation.aspects.integration

import com.reservation.config.annotations.DistributedLock
import com.reservation.config.annotations.RateLimiter
import com.reservation.enumeration.LockType.LOCK
import com.reservation.enumeration.RateLimitType.WHOLE
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit

open class AspectIntegrationTestService(
    private val aspectIntegrationTestDomainService: AspectIntegrationTestDomainService,
) {
    @DistributedLock(
        key = "'test:lock'+#parameter",
        lockType = LOCK,
        waitTime = 1,
        waitTimeUnit = TimeUnit.SECONDS,
    )
    @RateLimiter(
        key = "'test:rateLimiter'+#parameter",
        type = WHOLE,
        rate = 1000,
        rateIntervalTime = 1,
        rateIntervalTimeUnit = TimeUnit.SECONDS,
        maximumWaitTime = 5,
        maximumWaitTimeUnit = TimeUnit.SECONDS,
        bucketLiveTime = 1,
        bucketLiveTimeUnit = TimeUnit.MINUTES,
    )
    @Transactional
    open fun aspectLayerTest(parameter: String): String {
        return aspectIntegrationTestDomainService.decorateString(parameter)
    }
}
