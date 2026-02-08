package com.reservation.redis.redisson.lock.named.adapter

import com.reservation.redis.redisson.lock.AcquireLockTemplate
import org.springframework.stereotype.Component
import java.lang.Thread.sleep
import java.util.concurrent.TimeUnit

@Component
class AcquireNamedLockAdapter(
    private val acquireNamedLockRepository: AcquireNamedLockRepository,
) : AcquireLockTemplate {
    companion object {
        const val NAMED_LOCK_FALL_BACK_RATE = 0.5
    }

    override fun tryLock(
        name: String,
        waitTime: Long,
        waitTimeUnit: TimeUnit,
    ): Boolean {
        val totalMillis = waitTimeUnit.toMillis(waitTime)
        val sleepInterval = (totalMillis * NAMED_LOCK_FALL_BACK_RATE).toLong()

        val maxAttempt = totalMillis / sleepInterval
        var attempt = 0

        while (attempt < maxAttempt) {
            if (acquireNamedLockRepository.tryAcquireLock(name)) {
                return true
            }

            attempt++
            if (attempt < maxAttempt) {
                sleep(sleepInterval)
            }
        }

        return false
    }
}
