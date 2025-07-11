package com.reservation.restaurant.vo

import java.math.BigDecimal

data class RestaurantCoordinate(
    val latitude: BigDecimal,
    val longitude: BigDecimal,
) {
    companion object {
        private const val MAX_INTEGER_DIGITS = 3
        private const val MAX_DECIMAL_DIGITS = 20

        private fun isValidScale(value: BigDecimal): Boolean {
            val plain = value.stripTrailingZeros().toPlainString()
            val parts = plain.split(".")

            val integerPart = parts[0].replace("-", "").length
            val decimalPart = if (parts.size > 1) parts[1].length else 0

            return integerPart <= MAX_INTEGER_DIGITS && decimalPart <= MAX_DECIMAL_DIGITS
        }
    }

    init {
        require(isValidScale(latitude)) { "위도는 지수부 3자리, 소수부 20자리를 초과할 수 없습니다." }
        require(isValidScale(longitude)) { "경도는 지수부 3자리, 소수부 20자리를 초과할 수 없습니다." }
    }
}
