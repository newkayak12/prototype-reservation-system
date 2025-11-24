package com.reservation.redis.redisson.lock.general.store

import com.reservation.redis.config.RedissonNameSpace.FAIR_LOCK_NAMESPACE
import com.reservation.redis.redisson.common.lock.fair.exception.NoSuchLockException
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
    ): RLock =
        com.reservation.redis.redisson.lock.general.store.LockStore.LOCK.get().computeIfAbsent(
            com.reservation.redis.redisson.lock.general.store.LockStore.key(
                name,
            ),
        ) { lockProvider() }

    fun getFairLock(name: String): RLock =
        com.reservation.redis.redisson.lock.general.store.LockStore.LOCK.get()[
            com.reservation.redis.redisson.lock.general.store.LockStore.key(
                name,
            ),
        ] ?: throw NoSuchLockException()
}
