package com.reservation.restaurant.policy.validations

class RestaurantZipCodeFormatValidationPolicy(
    override val reason: String = "Invalid zipcode format",
) : RestaurantZipCodePolicy {
    companion object {
        private val RESTAURANT_ZIP_CODE_REGEXP = Regex("^\\d{5}\$")
    }

    override fun validate(zipCode: String): Boolean {
        return RESTAURANT_ZIP_CODE_REGEXP.matches(zipCode)
    }
}
