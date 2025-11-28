package com.reservation.timetable.port.output

import java.time.Duration

interface AcquireTimeTableSemaphore {
    fun tryAcquire(
        name: String,
        semaphoreSettings: SemaphoreSettings,
        semaphoreInquiry: SemaphoreInquiry,
    ): Boolean

    data class SemaphoreSettings(
        val capacity: Int,
        val semaphoreDuration: Duration,
    )

    data class SemaphoreInquiry(
        val permits: Int,
        val waitTime: Duration,
    )
}
