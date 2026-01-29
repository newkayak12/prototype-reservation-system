package com.reservation.redis.redisson.lock.util

import com.reservation.redis.config.RedissonNameSpace.FAIR_LOCK_NAMESPACE
import com.reservation.redis.config.RedissonNameSpace.LOCK_NAMESPACE

object LockKeyGenerator {
    fun lockKey(name: String) = "$LOCK_NAMESPACE$name"

    fun fairLockKey(name: String) = "$FAIR_LOCK_NAMESPACE$name"
}
