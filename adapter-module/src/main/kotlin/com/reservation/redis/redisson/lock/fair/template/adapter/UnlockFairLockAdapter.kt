package com.reservation.redis.redisson.lock.fair.template.adapter

import org.springframework.stereotype.Component

@Component
class UnlockFairLockAdapter : com.reservation.redis.redisson.lock.UnlockLockTemplate {
    override fun unlock(name: String) {
        val lock = com.reservation.redis.redisson.lock.general.store.LockStore.getFairLock(name)
        lock.unlock()
    }
}
