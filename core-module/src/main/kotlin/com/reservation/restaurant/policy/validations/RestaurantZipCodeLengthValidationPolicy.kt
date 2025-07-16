package com.reservation.restaurant.policy.validations

class RestaurantZipCodeLengthValidationPolicy(
    override val reason: String = "Invalid zipcode length",
) : RestaurantZipCodePolicy {
    companion object {
        private const val RESTAURANT_ZIP_CODE_LENGTH = 5
    }

    override fun validate(zipCode: String): Boolean {
        return zipCode.length == RESTAURANT_ZIP_CODE_LENGTH
    }
}
