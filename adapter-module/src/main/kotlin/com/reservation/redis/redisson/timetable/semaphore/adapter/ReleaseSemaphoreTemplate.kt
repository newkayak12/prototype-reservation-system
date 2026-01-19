package com.reservation.redis.redisson.timetable.semaphore.adapter

import com.reservation.redis.redisson.timetable.semaphore.util.SemaphoreKeyGenerator
import com.reservation.timetable.port.output.ReleaseSemaphore
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component

@Component
class ReleaseSemaphoreTemplate(
    private val redissonClient: RedissonClient,
) : ReleaseSemaphore {
    override fun release(name: String) {
        val semaphoreKey = SemaphoreKeyGenerator.key(name)
        val semaphore = redissonClient.getSemaphore(semaphoreKey)

        if (!semaphore.isExists) return

        semaphore.release()
    }
}
