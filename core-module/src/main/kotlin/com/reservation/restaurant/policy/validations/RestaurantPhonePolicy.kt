package com.reservation.restaurant.policy.validations

interface RestaurantPhonePolicy : RestaurantUnifiedValidationPolicy<String> {
    override fun validate(phoneNumber: String): Boolean
}
