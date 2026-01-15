package com.reservation.aspects.integration

import com.reservation.config.annotations.DistributedLock
import com.reservation.enumeration.LockType.LOCK
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
    @Transactional
    open fun aspectLayerTest(parameter: String): String {
        return aspectIntegrationTestDomainService.decorateString(parameter)
    }
}
