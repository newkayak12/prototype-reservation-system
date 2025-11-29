package com.reservation.redis.redisson.timetable.semaphore

import com.reservation.redis.redisson.semaphore.exception.NoSuchSemaphoreException
import org.redisson.api.RSemaphore

object SemaphoreStore {
    private val SEMAPHORE: ThreadLocal<MutableMap<String, RSemaphore>> =
        ThreadLocal.withInitial {
            mutableMapOf()
        }

    private val ACQUIRED: ThreadLocal<MutableMap<String, Boolean>> =
        ThreadLocal.withInitial { mutableMapOf() }

    fun key(name: String) = "$SEMAPHORE$name"

    fun getOrCreateSemaphore(
        name: String,
        semaphoreProvider: () -> RSemaphore,
    ): RSemaphore = SEMAPHORE.get().computeIfAbsent(key(name)) { semaphoreProvider() }

    fun getSemaphore(name: String) =
        SEMAPHORE.get()[key(name)]
            ?: throw NoSuchSemaphoreException()

    fun acquired(name: String) {
        ACQUIRED.get().computeIfAbsent(key(name)) { true }
    }

    fun released(name: String) {
        ACQUIRED.get().remove(key(name))
    }

    fun isAcquired(name: String) = ACQUIRED.get()?.get(key(name)) ?: false
}
