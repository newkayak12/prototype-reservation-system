package com.reservation.restaurant.service.validate

import com.reservation.restaurant.exceptions.InvalidateRestaurantElementException
import com.reservation.restaurant.policy.validations.RestaurantIntroduceLengthPolicy
import com.reservation.restaurant.policy.validations.RestaurantIntroducePolicy
import com.reservation.restaurant.policy.validations.RestaurantUnifiedValidationPolicy

class ValidateIntroduce {
    private val restaurantIntroductionPolicy: List<RestaurantIntroducePolicy> =
        listOf(
            RestaurantIntroduceLengthPolicy(),
        )

    private fun <T : RestaurantUnifiedValidationPolicy<String>> List<T>.validatePolicies(
        target: String,
    ) = firstOrNull { !it.validate(target) }
        ?.let {
            throw InvalidateRestaurantElementException(it.reason)
        }

    /**
     * 음식점 소개에 대한 검증을 진행합니다.
     * - 글자 수에 대해서 검증합니다.
     */
    fun validate(introduction: String) {
        restaurantIntroductionPolicy.validatePolicies(introduction)
    }
}
