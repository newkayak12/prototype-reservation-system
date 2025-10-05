package com.reservation.schedule.policy.validation

import java.time.LocalDate

interface HolidayDatePolicy {
    val reason: String

    fun validate(date: LocalDate): Boolean
}
