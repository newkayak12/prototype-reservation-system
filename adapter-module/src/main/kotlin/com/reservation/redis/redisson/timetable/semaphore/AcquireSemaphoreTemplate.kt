package com.reservation.redis.redisson.timetable.semaphore

import com.reservation.timetable.port.output.AcquireTimeTableSemaphore
import com.reservation.timetable.port.output.AcquireTimeTableSemaphore.SemaphoreInquiry
import com.reservation.timetable.port.output.AcquireTimeTableSemaphore.SemaphoreSettings
import org.redisson.api.RSemaphore
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component

@Component
class AcquireSemaphoreTemplate(
    private val redissonClient: RedissonClient,
) : AcquireTimeTableSemaphore {
    override fun tryAcquire(
        name: String,
        semaphoreSettings: SemaphoreSettings,
        semaphoreInquiry: SemaphoreInquiry,
    ): Boolean {
        val semaphore =
            SemaphoreStore.getOrCreateSemaphore(name) {
                redissonClient.getSemaphore(SemaphoreStore.key(name))
                    .apply { applySettings(semaphoreSettings) }
            }

        val permits = semaphoreInquiry.permits
        val waitTime = semaphoreInquiry.waitTime
        return semaphore.tryAcquire(permits, waitTime)
    }

    private fun RSemaphore.applySettings(settings: SemaphoreSettings) {
        if (isExists) return

        val capacity = settings.capacity
        val semaphoreDuration = settings.semaphoreDuration
        trySetPermits(capacity, semaphoreDuration)
    }
}
