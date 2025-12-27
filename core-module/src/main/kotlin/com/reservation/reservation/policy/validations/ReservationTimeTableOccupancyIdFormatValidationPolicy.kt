package com.reservation.reservation.policy.validations

class ReservationTimeTableOccupancyIdFormatValidationPolicy(
    override val reason: String = "Time Table Occupancy ID has invalid format.",
) : ReservationTimeTableOccupancyIdPolicy {
    companion object {
        private const val FORMAT =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        private val UUID_REGEX = Regex(FORMAT)
    }

    override fun validate(target: String): Boolean = target.matches(UUID_REGEX)
}
