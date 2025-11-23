package com.reservation.redis.redisson.timetable.lock.fair

import com.reservation.redis.config.RedissonNameSpace.FAIR_LOCK_NAMESPACE
import com.reservation.redis.redisson.common.lock.fair.exception.NoSuchFairLockException
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
    ): RLock = FAIR_LOCK.get().computeIfAbsent(key(name)) { lockProvider() }

    fun getFairLock(name: String): RLock =
        FAIR_LOCK.get()[key(name)] ?: throw NoSuchFairLockException()
}
