package com.reservation.redis.redisson.lock.fair

import com.reservation.redis.redisson.lock.AcquireLockTemplate
import com.reservation.redis.redisson.lock.CheckLockTemplate
import com.reservation.redis.redisson.lock.LockCoordinator
import com.reservation.redis.redisson.lock.UnlockLockTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class FairLockRedisCoordinator(
    private val acquireFairLockAdapter: AcquireLockTemplate,
    private val checkFairLockAdapter: CheckLockTemplate,
    private val unlockFairLockAdapter: UnlockLockTemplate,
) : LockCoordinator {
    override fun tryLock(
        name: String,
        waitTime: Long,
        waitTimeUnit: TimeUnit,
    ): Boolean =
        acquireFairLockAdapter.tryLock(
            name = name,
            waitTime = waitTime,
            waitTimeUnit = waitTimeUnit,
        )

    override fun isHeldByCurrentThread(parsedKey: String): Boolean =
        checkFairLockAdapter.isHeldByCurrentThread(parsedKey)

    override fun unlock(name: String) {
        unlockFairLockAdapter.unlock(name)
    }
}
