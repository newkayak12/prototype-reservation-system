package com.reservation.schedule.policy.validation

import java.time.LocalTime

interface TimeSpanTimePolicy {
    val reason: String

    fun validate(
        startTime: LocalTime,
        endTime: LocalTime,
    ): Boolean
}
