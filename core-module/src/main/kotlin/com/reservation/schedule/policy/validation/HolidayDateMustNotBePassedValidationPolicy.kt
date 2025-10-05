package com.reservation.schedule.policy.validation

import java.time.LocalDate

class HolidayDateMustNotBePassedValidationPolicy(
    override val reason: String = "date must not be passed",
) : HolidayDatePolicy {
    override fun validate(date: LocalDate): Boolean = date.isAfter(LocalDate.now())
}
