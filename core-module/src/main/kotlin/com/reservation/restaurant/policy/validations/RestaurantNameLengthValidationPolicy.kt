package com.reservation.restaurant.policy.validations

class RestaurantNameLengthValidationPolicy(
    override val reason: String = "restaurant name is too long",
) : RestaurantNamePolicy {
    companion object {
        private const val RESTAURANT_NAME_MIN_LENGTH = 0
        private const val RESTAURANT_NAME_MAX_LENGTH = 64
    }

    override fun validate(name: String): Boolean {
        return name.length in RESTAURANT_NAME_MIN_LENGTH..RESTAURANT_NAME_MAX_LENGTH
    }
}
