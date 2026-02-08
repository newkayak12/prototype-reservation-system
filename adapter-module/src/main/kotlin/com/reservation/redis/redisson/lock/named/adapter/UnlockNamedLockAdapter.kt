package com.reservation.redis.redisson.lock.named.adapter

import com.reservation.redis.redisson.lock.UnlockLockTemplate
import org.springframework.stereotype.Component

@Component
class UnlockNamedLockAdapter(
    private val unlockNamedLockRepository: UnlockNamedLockRepository,
) : UnlockLockTemplate {
    override fun unlock(name: String) {
        unlockNamedLockRepository.releaseLock(name)
    }
}
