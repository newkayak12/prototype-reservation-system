package com.reservation.reservation.policy.validations

class ReservationRestaurantIdEmptyValidationPolicy(
    override val reason: String = "restaurantId is empty.",
) : ReservationRestaurantIdPolicy {
    override fun validate(target: String): Boolean {
        return target.isNotBlank()
    }
}
