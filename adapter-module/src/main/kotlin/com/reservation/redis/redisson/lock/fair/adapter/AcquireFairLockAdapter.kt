package com.reservation.redis.redisson.lock.fair.adapter

import com.reservation.redis.redisson.lock.AcquireLockTemplate
import com.reservation.redis.redisson.lock.util.LockKeyGenerator
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class AcquireFairLockAdapter(
    private val redissonClient: RedissonClient,
) : AcquireLockTemplate {
    override fun tryLock(
        name: String,
        waitTime: Long,
        waitTimeUnit: TimeUnit,
    ): Boolean {
        val lock = redissonClient.getFairLock(LockKeyGenerator.fairLockKey(name))
        return lock.tryLock(waitTime, waitTimeUnit)
    }
}
