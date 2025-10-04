package com.reservation.schedule.policy.validation

import org.springframework.util.StringUtils

class ScheduleRestaurantIdEmptyValidationPolicy(
    override val reason: String = "restaurant id must not be null",
) : ScheduleRestaurantIdPolicy {
    override fun validate(restaurantId: String): Boolean = StringUtils.hasText(restaurantId)
}
