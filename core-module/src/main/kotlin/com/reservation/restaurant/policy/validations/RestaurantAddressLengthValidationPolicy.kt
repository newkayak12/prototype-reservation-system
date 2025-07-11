package com.reservation.restaurant.policy.validations

class RestaurantAddressLengthValidationPolicy(
    override val reason: String =
        "Address length should be shorter than $RESTAURANT_ADDRESS_MAX_LENGTH",
) : RestaurantAddressPolicy {
    companion object {
        private const val RESTAURANT_ADDRESS_MAX_LENGTH = 256
    }

    override fun validate(address: String): Boolean {
        return address.length < RESTAURANT_ADDRESS_MAX_LENGTH
    }
}
