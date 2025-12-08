package com.reservation.timetable.policy.validation

class TimeTableOccupancyIdFormatValidationPolicy(
    override val reason: String = "Time Table ID has invalid format.",
) : TimeTableOccupancyIdPolicy {
    companion object {
        private const val FORMAT =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        private val UUID_REGEX = Regex(FORMAT)
    }

    override fun validate(timeTableOccupancyId: String): Boolean =
        timeTableOccupancyId.matches(UUID_REGEX)
}
