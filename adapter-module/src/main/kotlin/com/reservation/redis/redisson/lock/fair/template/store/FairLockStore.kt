package com.reservation.redis.redisson.lock.fair.template.store

import com.reservation.redis.config.RedissonNameSpace.FAIR_LOCK_NAMESPACE
import com.reservation.redis.redisson.lock.general.exception.NoSuchLockException
import org.redisson.api.RLock

object FairLockStore {
    private val FAIR_LOCK: ThreadLocal<MutableMap<String, RLock>> =
        ThreadLocal.withInitial {
            mutableMapOf()
        }

    fun key(name: String) = "$FAIR_LOCK_NAMESPACE$name"

    fun getOrCreateFairLock(
        name: String,
        lockProvider: () -> RLock,
    ): RLock =
        com.reservation.redis.redisson.lock.fair.template.store.FairLockStore.FAIR_LOCK.get()
            .computeIfAbsent(
                com.reservation.redis.redisson.lock.fair.template.store.FairLockStore.key(
                    name,
                ),
            ) { lockProvider() }

    fun getFairLock(name: String): RLock =
        com.reservation.redis.redisson.lock.fair.template.store.FairLockStore.FAIR_LOCK.get()[
            com.reservation.redis.redisson.lock.fair.template.store.FairLockStore.key(
                name,
            ),
        ] ?: throw NoSuchLockException()
}
