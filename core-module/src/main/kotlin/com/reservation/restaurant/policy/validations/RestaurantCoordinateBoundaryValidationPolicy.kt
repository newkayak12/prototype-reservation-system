package com.reservation.restaurant.policy.validations

import java.math.BigDecimal

class RestaurantCoordinateBoundaryValidationPolicy(
    override val reason: String = "Coordinate is out of range",
) : RestaurantCoordinatePolicy {
    companion object {
        private val MAX_LATITUDE = BigDecimal.valueOf(38.7)
        private val MAX_LONGITUDE = BigDecimal.valueOf(131.2)

        private val MIN_LATITUDE = BigDecimal.valueOf(33.0)
        private val MIN_LONGITUDE = BigDecimal.valueOf(124.0)
    }

    override fun validate(coordinate: Pair<BigDecimal, BigDecimal>): Boolean {
        val (latitude, longitude) = coordinate
        return validateLatitude(latitude) && validateLongitude(longitude)
    }

    private fun validateLatitude(latitude: BigDecimal): Boolean {
        return latitude.isLargerThan(MIN_LATITUDE) &&
            latitude.isLessThan(MAX_LATITUDE)
    }

    private fun validateLongitude(longitude: BigDecimal): Boolean {
        return longitude.isLargerThan(MIN_LONGITUDE) &&
            longitude.isLessThan(MAX_LONGITUDE)
    }

    private fun BigDecimal.isLargerThan(target: BigDecimal): Boolean = this.compareTo(target) > 0

    private fun BigDecimal.isLessThan(target: BigDecimal): Boolean = this.compareTo(target) < 0
}
