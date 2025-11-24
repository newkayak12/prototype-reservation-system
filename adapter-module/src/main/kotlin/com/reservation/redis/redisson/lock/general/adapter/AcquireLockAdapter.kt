package com.reservation.redis.redisson.lock.general.adapter

import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class AcquireLockAdapter(
    private val redissonClient: RedissonClient,
) : com.reservation.redis.redisson.lock.AcquireLockTemplate {
    override fun tryLock(
        name: String,
        waitTime: Long,
        waitTimeUnit: TimeUnit,
    ): Boolean {
        val lock =
            com.reservation.redis.redisson.lock.general.store.LockStore.getOrCreateFairLock(name) {
                redissonClient.getLock(
                    com.reservation.redis.redisson.lock.general.store.LockStore.key(name),
                )
            }
        return lock.tryLock(waitTime, waitTimeUnit)
    }
}
