package com.reservation.redis.redisson.lock.fair.adapter

import com.reservation.redis.redisson.lock.UnlockLockTemplate
import com.reservation.redis.redisson.lock.util.LockKeyGenerator
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component

@Component
class UnlockFairLockAdapter(
    private val redissonClient: RedissonClient,
) : UnlockLockTemplate {
    override fun unlock(name: String) {
        val lock = redissonClient.getFairLock(LockKeyGenerator.fairLockKey(name))
        lock.unlock()
    }
}
