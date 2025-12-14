package com.reservation.reservation.policy.validations

class ReservationTimeTableOccupancyIdEmptyValidationPolicy(
    override val reason: String = "timeTableOccupancyId is empty.",
) : ReservationTimeTableOccupancyIdPolicy {
    override fun validate(target: String): Boolean {
        return target.isNotBlank()
    }
}
