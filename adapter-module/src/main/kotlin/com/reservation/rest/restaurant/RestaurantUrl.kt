package com.reservation.rest.restaurant

object RestaurantUrl {
    const val PREFIX = "/api/v1/restaurant"
    const val CREATE_RESTAURANT = PREFIX
    const val CHANGE_RESTAURANT = "$PREFIX/{id:[0-9a-fA-F\\-]{36}}"
    const val FIND_A_RESTAURANT = "$PREFIX/{id:[0-9a-fA-F\\-]{36}}"
    const val FIND_A_RESTAURANTS = PREFIX
}
