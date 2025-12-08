package com.reservation.timetable.policy.validation

class TimeTableOccupancyIdIsNotEmptyValidationPolicy(
    override val reason: String = "TimeTable ID is empty.",
) : TimeTableOccupancyIdPolicy {
    override fun validate(timeTableOccupancyId: String): Boolean = timeTableOccupancyId.isNotBlank()
}
