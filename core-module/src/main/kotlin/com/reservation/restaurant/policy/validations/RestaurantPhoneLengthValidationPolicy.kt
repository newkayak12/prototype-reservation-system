package com.reservation.restaurant.policy.validations

class RestaurantPhoneLengthValidationPolicy(
    override val reason: String =
        "Phone number length is between " +
            "$RESTAURANT_PHONE_MIN_LENGTH and $RESTAURANT_PHONE_MAX_LENGTH",
) : RestaurantPhonePolicy {
    companion object {
        private const val RESTAURANT_PHONE_MIN_LENGTH = 11
        private const val RESTAURANT_PHONE_MAX_LENGTH = 13
    }

    override fun validate(phoneNumber: String): Boolean {
        return phoneNumber.length in RESTAURANT_PHONE_MIN_LENGTH..RESTAURANT_PHONE_MAX_LENGTH
    }
}
