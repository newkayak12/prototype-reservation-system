package com.reservation.reservation.policy.validations

class ReservationUserIdEmptyValidationPolicy(
    override val reason: String = "userId is empty.",
) : ReservationUserIdPolicy {
    override fun validate(target: String): Boolean {
        return target.isNotBlank()
    }
}
