package com.reservation.redis.redisson.lock

interface CheckLockTemplate {
    fun isHeldByCurrentThread(name: String): Boolean
}
