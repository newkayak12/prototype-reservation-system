package com.reservation.restaurant.vo

data class RestaurantAddress(
    val zipCode: String,
    val address: String,
    val detail: String,
    val coordinate: RestaurantCoordinate,
)
