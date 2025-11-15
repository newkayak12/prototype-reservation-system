package com.reservation.schedule.policy.validation

class TimeSpanRestaurantIdEmptyValidationPolicy(
    override val reason: String = "restaurant id must not be null",
) : TimeSpanRestaurantIdPolicy {
    override fun validate(restaurantId: String): Boolean = !restaurantId.isNullOrBlank()
}
