package com.reservation.timetable.policy.validation

class TimeTableIdFormatValidationPolicy(
    override val reason: String = "Time Table ID has invalid format.",
) : TimeTableIdPolicy {
    companion object {
        private const val FORMAT =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        private val UUID_REGEX = Regex(FORMAT)
    }

    override fun validate(timeTableId: String): Boolean = timeTableId.matches(UUID_REGEX)
}
