package com.reservation.restaurant.service.validate

import com.reservation.restaurant.exceptions.InvalidateRestaurantElementException
import com.reservation.restaurant.policy.validations.RestaurantCoordinateBoundaryValidationPolicy
import com.reservation.restaurant.policy.validations.RestaurantCoordinateFormatValidationPolicy
import com.reservation.restaurant.policy.validations.RestaurantCoordinatePolicy
import com.reservation.restaurant.policy.validations.RestaurantUnifiedValidationPolicy
import java.math.BigDecimal

class ValidateCoordinate {
    private val restaurantCoordinatePolicy: List<RestaurantCoordinatePolicy> =
        listOf(
            RestaurantCoordinateFormatValidationPolicy(),
            RestaurantCoordinateBoundaryValidationPolicy(),
        )

    private fun <
        T : RestaurantUnifiedValidationPolicy<Pair<BigDecimal, BigDecimal>>,
    > List<T>.validatePolicies(
        target: Pair<BigDecimal, BigDecimal>,
    ) {
        firstOrNull { !it.validate(target) }
            ?.let {
                throw InvalidateRestaurantElementException(it.reason)
            }
    }

    /**
     * 음식점 좌표에 대해서 검증을 진행합니다.
     * - 위도, 경도의 형식에 대해서 검증합니다.
     * - 위도, 겅도가 한국에 국한되어 있는지 검증합니다.
     */
    fun validate(
        latitude: BigDecimal,
        longitude: BigDecimal,
    ) {
        restaurantCoordinatePolicy.validatePolicies(latitude to longitude)
    }
}
