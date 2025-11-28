package com.reservation.redis.redisson.lock

interface UnlockLockTemplate {
    fun unlock(name: String)
}
