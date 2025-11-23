package com.reservation.timetable.policy.validation

interface UserIdPolicy {
    val reason: String

    fun validate(userId: String): Boolean
}
