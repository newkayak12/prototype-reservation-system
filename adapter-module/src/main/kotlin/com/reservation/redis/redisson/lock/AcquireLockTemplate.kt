package com.reservation.redis.redisson.lock

import java.util.concurrent.TimeUnit

interface AcquireLockTemplate {
    fun tryLock(
        name: String,
        waitTime: Long,
        waitTimeUnit: TimeUnit,
    ): Boolean
}
