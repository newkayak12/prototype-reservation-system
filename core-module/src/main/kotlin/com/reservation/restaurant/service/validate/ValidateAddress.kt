package com.reservation.restaurant.service.validate

import com.reservation.restaurant.exceptions.InvalidateRestaurantElementException
import com.reservation.restaurant.policy.validations.RestaurantAddressEmptyValidationPolicy
import com.reservation.restaurant.policy.validations.RestaurantAddressLengthValidationPolicy
import com.reservation.restaurant.policy.validations.RestaurantAddressPolicy
import com.reservation.restaurant.policy.validations.RestaurantUnifiedValidationPolicy

class ValidateAddress {
    private val restaurantAddressPolicy: List<RestaurantAddressPolicy> =
        listOf(
            RestaurantAddressEmptyValidationPolicy(),
            RestaurantAddressLengthValidationPolicy(),
        )

    private fun <T : RestaurantUnifiedValidationPolicy<String>> List<T>.validatePolicies(
        target: String,
    ) = firstOrNull { !it.validate(target) }
        ?.let {
            throw InvalidateRestaurantElementException(it.reason)
        }

    /**
     * 음식점 주소에 대해서 검증을 진행합니다.
     * - 음식점 주소가 비어있는지 검증홥니다.
     * - 음식점 주소의 길이에 대해서 검증합니다.
     */
    fun validate(address: String) {
        restaurantAddressPolicy.validatePolicies(address)
    }
}
