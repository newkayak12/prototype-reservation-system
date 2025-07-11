package com.reservation.restaurant.policy.validations

import java.math.BigDecimal

interface RestaurantCoordinatePolicy :
    RestaurantUnifiedValidationPolicy<Pair<BigDecimal, BigDecimal>> {
    override fun validate(coordinate: Pair<BigDecimal, BigDecimal>): Boolean
}
