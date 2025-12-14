package com.reservation.reservation.policy.validations

class ReservationTimeTableIdEmptyValidationPolicy(
    override val reason: String = "timeTableId is empty.",
) : ReservationTimeTableIdPolicy {
    override fun validate(target: String): Boolean {
        return target.isNotBlank()
    }
}
