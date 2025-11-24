package com.reservation.redis.redisson.lock.general.adapter

import org.springframework.stereotype.Component

@Component
class CheckLockAdapter : com.reservation.redis.redisson.lock.CheckLockTemplate {
    override fun isHeldByCurrentThread(name: String): Boolean {
        val lock = com.reservation.redis.redisson.lock.general.store.LockStore.getFairLock(name)
        return lock.isHeldByCurrentThread
    }
}
