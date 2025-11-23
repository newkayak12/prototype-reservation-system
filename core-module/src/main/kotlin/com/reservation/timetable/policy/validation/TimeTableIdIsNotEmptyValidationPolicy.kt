package com.reservation.timetable.policy.validation

class TimeTableIdIsNotEmptyValidationPolicy(
    override val reason: String = "TimeTable ID is empty.",
) : TimeTableIdPolicy {
    override fun validate(timeTableId: String): Boolean = timeTableId.isNotBlank()
}
