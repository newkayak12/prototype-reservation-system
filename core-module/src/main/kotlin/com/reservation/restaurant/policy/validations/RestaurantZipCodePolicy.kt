package com.reservation.restaurant.policy.validations

interface RestaurantZipCodePolicy : RestaurantUnifiedValidationPolicy<String> {
    override fun validate(zipCode: String): Boolean
}
