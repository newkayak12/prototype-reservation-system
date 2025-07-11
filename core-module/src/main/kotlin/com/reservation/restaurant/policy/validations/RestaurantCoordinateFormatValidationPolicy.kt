package com.reservation.restaurant.policy.validations

import java.math.BigDecimal

class RestaurantCoordinateFormatValidationPolicy(
    override val reason: String = "Invalid coordinate format",
) : RestaurantCoordinatePolicy {
    companion object {
        private const val MAX_INTEGER_DIGITS = 3
        private const val MAX_DECIMAL_DIGITS = 20
    }

    private fun isValidScale(value: BigDecimal): Boolean {
        val plain = value.stripTrailingZeros().toPlainString()
        val parts = plain.split(".")

        val integerPart = parts[0].replace("-", "").length
        val decimalPart = if (parts.size > 1) parts[1].length else 0

        return integerPart <= MAX_INTEGER_DIGITS && decimalPart <= MAX_DECIMAL_DIGITS
    }

    override fun validate(coordinate: Pair<BigDecimal, BigDecimal>): Boolean {
        val (latitude, longitude) = coordinate
        return isValidScale(latitude) && isValidScale(longitude)
    }
}
