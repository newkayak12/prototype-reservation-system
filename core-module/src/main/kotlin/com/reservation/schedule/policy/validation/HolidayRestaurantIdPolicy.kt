package com.reservation.schedule.policy.validation

interface HolidayRestaurantIdPolicy {
    val reason: String

    fun validate(restaurantId: String): Boolean
}
