package com.reservation.restaurant.service.validate

import com.reservation.restaurant.exceptions.InvalidateRestaurantElementException
import com.reservation.restaurant.policy.validations.RestaurantPhoneEmptyValidationPolicy
import com.reservation.restaurant.policy.validations.RestaurantPhoneFormatValidationPolicy
import com.reservation.restaurant.policy.validations.RestaurantPhoneLengthValidationPolicy
import com.reservation.restaurant.policy.validations.RestaurantPhonePolicy
import com.reservation.restaurant.policy.validations.RestaurantUnifiedValidationPolicy

class ValidatePhone {
    private val restaurantPhonePolicy: List<RestaurantPhonePolicy> =
        listOf(
            RestaurantPhoneEmptyValidationPolicy(),
            RestaurantPhoneLengthValidationPolicy(),
            RestaurantPhoneFormatValidationPolicy(),
        )

    private fun <T : RestaurantUnifiedValidationPolicy<String>> List<T>.validatePolicies(
        target: String,
    ) = firstOrNull { !it.validate(target) }
        ?.let {
            throw InvalidateRestaurantElementException(it.reason)
        }

    /**
     * 음식점 전화번호에 대한 검증을 진행합니다.
     * - 전화번호가 비어있는지 검증합니다.
     * - 전화번호의 길이에 대해서 검증합니다.
     * - 전화번호 형식에 대해서 검증합니다.
     */
    fun validate(phone: String) {
        restaurantPhonePolicy.validatePolicies(phone)
    }
}
