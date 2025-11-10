package com.reservation.schedule.policy.validation

class TimeSpanRestaurantIdFormatValidationPolicy(
    override val reason: String = "Invalid ID Format",
) : TimeSpanRestaurantIdPolicy {
    companion object {
        private const val FORMAT =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        private val UUID_REGEX = Regex(FORMAT)
    }

    override fun validate(restaurantId: String): Boolean = UUID_REGEX.matches(restaurantId)
}
