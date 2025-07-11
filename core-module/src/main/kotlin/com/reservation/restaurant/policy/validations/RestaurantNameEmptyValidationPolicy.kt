package com.reservation.restaurant.policy.validations

class RestaurantNameEmptyValidationPolicy(
    override val reason: String = "restaurant name is empty",
) : RestaurantNamePolicy {
    override fun validate(name: String): Boolean {
        return name.isNotBlank()
    }
}
