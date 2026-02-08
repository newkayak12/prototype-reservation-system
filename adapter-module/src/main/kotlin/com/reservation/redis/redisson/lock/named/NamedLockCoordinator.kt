package com.reservation.redis.redisson.lock.named

import com.reservation.redis.redisson.lock.AcquireLockTemplate
import com.reservation.redis.redisson.lock.LockCoordinator
import com.reservation.redis.redisson.lock.UnlockLockTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class NamedLockCoordinator(
    private val acquireNamedLockAdapter: AcquireLockTemplate,
    private val unlockNamedLockAdapter: UnlockLockTemplate,
) : LockCoordinator {
    override fun tryLock(
        name: String,
        waitTime: Long,
        waitTimeUnit: TimeUnit,
    ): Boolean = acquireNamedLockAdapter.tryLock(name, waitTime, waitTimeUnit)

    override fun isHeldByCurrentThread(parsedKey: String): Boolean = true

    override fun unlock(name: String) {
        unlockNamedLockAdapter.unlock(name)
    }
}
