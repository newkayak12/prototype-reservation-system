package com.reservation.redis.redisson.timetable.semaphore

import com.reservation.redis.redisson.common.semaphore.exception.NoSuchSemaphoreException
import com.reservation.redis.redisson.timetable.lock.fair.FairLockStore
import org.redisson.api.RSemaphore

object SemaphoreStore {
    private val SEMAPHORE: ThreadLocal<MutableMap<String, RSemaphore>> =
        ThreadLocal.withInitial {
            mutableMapOf()
        }

    fun key(name: String) = "$SEMAPHORE$name"

    fun getOrCreateSemaphore(
        name: String,
        semaphoreProvider: () -> RSemaphore,
    ): RSemaphore = SEMAPHORE.get().computeIfAbsent(key(name)) { semaphoreProvider() }

    fun getSemaphore(name: String) =
        SEMAPHORE.get()[FairLockStore.key(name)]
            ?: throw NoSuchSemaphoreException()
}
