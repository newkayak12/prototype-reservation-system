package com.reservation.restaurant.policy.validations

interface RestaurantAddressPolicy : RestaurantUnifiedValidationPolicy<String> {
    override fun validate(address: String): Boolean
}
