package com.reservation.restaurant.service.validate

import com.reservation.restaurant.exceptions.InvalidateRestaurantElementException
import com.reservation.restaurant.policy.validations.RestaurantNameEmptyValidationPolicy
import com.reservation.restaurant.policy.validations.RestaurantNameLengthValidationPolicy
import com.reservation.restaurant.policy.validations.RestaurantNamePolicy
import com.reservation.restaurant.policy.validations.RestaurantUnifiedValidationPolicy

class ValidateName {
    private val restaurantNamePolicy: List<RestaurantNamePolicy> =
        listOf(
            RestaurantNameEmptyValidationPolicy(),
            RestaurantNameLengthValidationPolicy(),
        )

    private fun <T : RestaurantUnifiedValidationPolicy<String>> List<T>.validatePolicies(
        target: String,
    ) = firstOrNull { !it.validate(target) }
        ?.let {
            throw InvalidateRestaurantElementException(it.reason)
        }

    /**
     * 음식점 이름에 대한 검증을 진행합니다.
     * - 음식점 이름이 비어있는지 검증합니다.
     * - 글자 수에 대해서 검증합니다.
     */
    fun validate(name: String) = restaurantNamePolicy.validatePolicies(name)
}
