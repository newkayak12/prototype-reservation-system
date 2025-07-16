package com.reservation.restaurant.vo

import java.math.BigDecimal

data class RestaurantAddress(
    val zipCode: String,
    val address: String,
    val detail: String,
    val coordinate: RestaurantCoordinate,
) {
    val coordinateX: BigDecimal
        get() = coordinate.latitude
    val coordinateY: BigDecimal
        get() = coordinate.longitude
}
