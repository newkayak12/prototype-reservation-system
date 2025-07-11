package com.reservation.restaurant.policy.validations

interface RestaurantNamePolicy : RestaurantUnifiedValidationPolicy<String> {
    override fun validate(name: String): Boolean
}
