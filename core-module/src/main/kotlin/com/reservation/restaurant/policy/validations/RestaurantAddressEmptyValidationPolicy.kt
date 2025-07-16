package com.reservation.restaurant.policy.validations

class RestaurantAddressEmptyValidationPolicy(
    override val reason: String = "Restaurant Address is Empty",
) : RestaurantAddressPolicy {
    override fun validate(address: String): Boolean {
        return address.isNotBlank()
    }
}
