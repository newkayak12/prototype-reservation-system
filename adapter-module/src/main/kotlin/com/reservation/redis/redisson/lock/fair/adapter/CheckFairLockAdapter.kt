package com.reservation.redis.redisson.lock.fair.adapter

import com.reservation.redis.redisson.lock.CheckLockTemplate
import com.reservation.redis.redisson.lock.util.LockKeyGenerator
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component

@Component
class CheckFairLockAdapter(
    private val redissonClient: RedissonClient,
) : CheckLockTemplate {
    override fun isHeldByCurrentThread(name: String): Boolean {
        val lock = redissonClient.getFairLock(LockKeyGenerator.fairLockKey(name))
        return lock.isHeldByCurrentThread
    }
}
