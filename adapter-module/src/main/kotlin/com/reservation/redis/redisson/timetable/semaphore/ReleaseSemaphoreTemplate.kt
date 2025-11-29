package com.reservation.redis.redisson.timetable.semaphore

import com.reservation.timetable.port.output.ReleaseSemaphore
import org.springframework.stereotype.Component

@Component
class ReleaseSemaphoreTemplate : ReleaseSemaphore {
    override fun release(name: String) {
        val semaphore = SemaphoreStore.getSemaphore(name)
        if (SemaphoreStore.isAcquired(name)) {
            semaphore.release()
            SemaphoreStore.released(name)
        }
    }
}
