package com.reservation.schedule.policy.validation

class HolidayRestaurantIdEmptyValidationPolicy(
    override val reason: String = "restaurant id must not be null",
) : HolidayRestaurantIdPolicy {
    override fun validate(restaurantId: String): Boolean = !restaurantId.isNullOrBlank()
}
