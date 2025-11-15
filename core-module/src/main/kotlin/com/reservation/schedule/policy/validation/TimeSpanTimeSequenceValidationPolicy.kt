package com.reservation.schedule.policy.validation

import java.time.LocalTime

class TimeSpanTimeSequenceValidationPolicy(
    override val reason: String = "start time must be before end time.",
) : TimeSpanTimePolicy {
    override fun validate(
        startTime: LocalTime,
        endTime: LocalTime,
    ): Boolean = startTime.isBefore(endTime)
}
