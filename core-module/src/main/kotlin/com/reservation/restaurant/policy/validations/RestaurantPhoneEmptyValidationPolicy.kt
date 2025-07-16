package com.reservation.restaurant.policy.validations

class RestaurantPhoneEmptyValidationPolicy(
    override val reason: String = "Phone number is empty.",
) : RestaurantPhonePolicy {
    override fun validate(phoneNumber: String): Boolean {
        return phoneNumber.isNotBlank()
    }
}
