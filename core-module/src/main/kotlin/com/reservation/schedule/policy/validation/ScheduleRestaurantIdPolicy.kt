package com.reservation.schedule.policy.validation

interface ScheduleRestaurantIdPolicy {
    val reason: String

    fun validate(restaurantId: String): Boolean
}
