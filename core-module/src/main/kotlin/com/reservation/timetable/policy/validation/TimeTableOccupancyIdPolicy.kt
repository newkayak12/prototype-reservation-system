package com.reservation.timetable.policy.validation

interface TimeTableOccupancyIdPolicy {
    val reason: String

    fun validate(timeTableOccupancyId: String): Boolean
}
