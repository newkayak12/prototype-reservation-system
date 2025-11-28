package com.reservation.timetable.policy.validation

interface TimeTableIdPolicy {
    val reason: String

    fun validate(timeTableId: String): Boolean
}
