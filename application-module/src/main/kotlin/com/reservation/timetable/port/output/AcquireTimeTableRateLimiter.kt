package com.reservation.timetable.port.output

import java.time.Duration

interface AcquireTimeTableRateLimiter {
    fun tryAcquire(
        name: String,
        duration: Duration,
        settings: RateLimitSettings,
    ): Boolean

    enum class RateLimitType {
        WHOLE,
        PER_CLIENT,
    }

    data class RateLimitSettings(
        val type: RateLimitType,
        val rate: Long,
        val rateInterval: Duration,
        val duration: Duration,
    )
}
