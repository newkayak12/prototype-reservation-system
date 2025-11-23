package com.reservation.timetable.policy.validation

class UserIdFormatValidationPolicy(
    override val reason: String = "User ID has invalid format.",
) : UserIdPolicy {
    companion object {
        private const val FORMAT =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        private val UUID_REGEX = Regex(FORMAT)
    }

    override fun validate(userId: String): Boolean = userId.matches(UUID_REGEX)
}
