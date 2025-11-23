package com.reservation.timetable.policy.validation

class UserIdIsNotEmptyValidationPolicy(
    override val reason: String = "User ID is empty.",
) : UserIdPolicy {
    override fun validate(userId: String): Boolean = userId.isNotBlank()
}
