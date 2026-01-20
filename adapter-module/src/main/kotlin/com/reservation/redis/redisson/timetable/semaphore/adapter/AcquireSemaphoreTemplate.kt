package com.reservation.redis.redisson.timetable.semaphore.adapter

import com.reservation.redis.redisson.timetable.semaphore.util.SemaphoreKeyGenerator
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
        return runCatching {
            val semaphore =
                redissonClient.getSemaphore(SemaphoreKeyGenerator.key(name))
                    .apply { applySettings(semaphoreSettings) }

            val permits = semaphoreInquiry.permits
            val waitTime = semaphoreInquiry.waitTime
            semaphore.tryAcquire(permits, waitTime)
        }
            .getOrElse { false }
    }

    private fun RSemaphore.applySettings(settings: SemaphoreSettings) {
        val capacity = settings.capacity
        val semaphoreDuration = settings.semaphoreDuration

        // trySetPermits는 이미 존재하는 경우 무시되므로 중복 체크 불필요
        trySetPermits(capacity, semaphoreDuration)
    }
}
