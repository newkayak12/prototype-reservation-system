package com.reservation.redis.redisson.timetable.semaphore.util

object SemaphoreKeyGenerator {
    private const val SEMAPHORE_PREFIX = "SEMAPHORE_"

    fun key(name: String) = "$SEMAPHORE_PREFIX$name"
}
