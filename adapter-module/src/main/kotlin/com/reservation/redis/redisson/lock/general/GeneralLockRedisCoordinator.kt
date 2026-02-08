package com.reservation.redis.redisson.lock.general

import com.reservation.redis.redisson.lock.AcquireLockTemplate
import com.reservation.redis.redisson.lock.CheckLockTemplate
import com.reservation.redis.redisson.lock.LockCoordinator
import com.reservation.redis.redisson.lock.UnlockLockTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class GeneralLockRedisCoordinator(
    private val acquireLockAdapter: AcquireLockTemplate,
    private val checkLockAdapter: CheckLockTemplate,
    private val unlockLockAdapter: UnlockLockTemplate,
) : LockCoordinator {
    override fun tryLock(
        name: String,
        waitTime: Long,
        waitTimeUnit: TimeUnit,
    ): Boolean =
        acquireLockAdapter.tryLock(
            name = name,
            waitTime = waitTime,
            waitTimeUnit = waitTimeUnit,
        )

    override fun isHeldByCurrentThread(parsedKey: String): Boolean =
        checkLockAdapter.isHeldByCurrentThread(parsedKey)

    override fun unlock(name: String) {
        unlockLockAdapter.unlock(name)
    }
}
