package com.reservation.schedule.policy.validation

interface TimeSpanRestaurantIdPolicy {
    val reason: String

    fun validate(restaurantId: String): Boolean
}
