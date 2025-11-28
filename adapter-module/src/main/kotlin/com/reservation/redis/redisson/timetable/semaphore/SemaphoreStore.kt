package com.reservation.redis.redisson.timetable.semaphore

import com.reservation.redis.redisson.lock.general.store.LockStore
import com.reservation.redis.redisson.semaphore.exception.NoSuchSemaphoreException
import org.redisson.api.RSemaphore

object SemaphoreStore {
    private val SEMAPHORE: ThreadLocal<MutableMap<String, RSemaphore>> =
        ThreadLocal.withInitial {
            mutableMapOf()
        }

    private val ACQUIRED: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    fun key(name: String) = "$SEMAPHORE$name"

    fun getOrCreateSemaphore(
        name: String,
        semaphoreProvider: () -> RSemaphore,
    ): RSemaphore = SEMAPHORE.get().computeIfAbsent(key(name)) { semaphoreProvider() }

    fun getSemaphore(name: String) =
        SEMAPHORE.get()[LockStore.key(name)]
            ?: throw NoSuchSemaphoreException()

    fun acquired() {
        ACQUIRED.set(true)
    }

    fun released() {
        ACQUIRED.set(false)
    }

    fun isAcquired() = ACQUIRED.get()
}
