package com.reservation.restaurant.service.validate

import com.reservation.restaurant.exceptions.InvalidateRestaurantElementException
import com.reservation.restaurant.policy.validations.RestaurantUnifiedValidationPolicy
import com.reservation.restaurant.policy.validations.RestaurantZipCodeFormatValidationPolicy
import com.reservation.restaurant.policy.validations.RestaurantZipCodeLengthValidationPolicy
import com.reservation.restaurant.policy.validations.RestaurantZipCodePolicy

class ValidateZipCode {
    private val restaurantZipCodePolicy: List<RestaurantZipCodePolicy> =
        listOf(
            RestaurantZipCodeLengthValidationPolicy(),
            RestaurantZipCodeFormatValidationPolicy(),
        )

    private fun <T : RestaurantUnifiedValidationPolicy<String>> List<T>.validatePolicies(
        target: String,
    ) = firstOrNull { !it.validate(target) }
        ?.let {
            throw InvalidateRestaurantElementException(it.reason)
        }

    /**
     * 음식점 우편번호에 대해서 검증을 진행합니다.
     * - 음식점 우편번호가 비어있는지 검증합니다.
     * - 음식점 우편번호 형식에 대해서 검증합니다.
     */
    fun validate(zipCode: String) {
        restaurantZipCodePolicy.validatePolicies(zipCode)
    }
}
