package com.reservation.redis.redisson.lock.general.store

import com.reservation.redis.config.RedissonNameSpace.FAIR_LOCK_NAMESPACE
import com.reservation.redis.redisson.lock.general.exception.NoSuchLockException
import org.redisson.api.RLock

object LockStore {
    private val LOCK: ThreadLocal<MutableMap<String, RLock>> =
        ThreadLocal.withInitial {
            mutableMapOf()
        }

    fun key(name: String) = "$FAIR_LOCK_NAMESPACE$name"

    fun getOrCreateFairLock(
        name: String,
        lockProvider: () -> RLock,
    ): RLock = LOCK.get().computeIfAbsent(key(name)) { lockProvider() }

    fun getFairLock(name: String): RLock = LOCK.get()[key(name)] ?: throw NoSuchLockException()
}
