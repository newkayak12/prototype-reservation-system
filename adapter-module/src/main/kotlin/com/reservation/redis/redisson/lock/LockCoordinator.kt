package com.reservation.redis.redisson.lock

import java.util.concurrent.TimeUnit

interface LockCoordinator {
    fun tryLock(
        name: String,
        waitTime: Long,
        waitTimeUnit: TimeUnit,
    ): Boolean

    fun isHeldByCurrentThread(parsedKey: String): Boolean

    fun unlock(name: String)
}
