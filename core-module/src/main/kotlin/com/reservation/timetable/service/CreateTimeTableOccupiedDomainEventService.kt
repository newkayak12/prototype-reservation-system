package com.reservation.timetable.service

import com.reservation.timetable.event.TimeTableOccupiedDomainEvent
import com.reservation.timetable.policy.exceptions.InvalidTimeTableIdException
import com.reservation.timetable.policy.exceptions.InvalidTimeTableOccupancyIdException
import com.reservation.timetable.policy.validation.TimeTableIdFormatValidationPolicy
import com.reservation.timetable.policy.validation.TimeTableIdIsNotEmptyValidationPolicy
import com.reservation.timetable.policy.validation.TimeTableIdPolicy
import com.reservation.timetable.policy.validation.TimeTableOccupancyIdFormatValidationPolicy
import com.reservation.timetable.policy.validation.TimeTableOccupancyIdIsNotEmptyValidationPolicy
import com.reservation.timetable.policy.validation.TimeTableOccupancyIdPolicy

class CreateTimeTableOccupiedDomainEventService {
    private val timeTableOccupancyIdPolicies: List<TimeTableOccupancyIdPolicy> =
        listOf(
            TimeTableOccupancyIdFormatValidationPolicy(),
            TimeTableOccupancyIdIsNotEmptyValidationPolicy(),
        )

    private val timeTableIdPolicies: List<TimeTableIdPolicy> =
        listOf(
            TimeTableIdIsNotEmptyValidationPolicy(),
            TimeTableIdFormatValidationPolicy(),
        )

    private fun <T : TimeTableIdPolicy> List<T>.validateTimeTableId(target: String?) =
        firstOrNull { !it.validate(target ?: "") }
            ?.let {
                throw InvalidTimeTableIdException(it.reason)
            }

    private fun <T : TimeTableOccupancyIdPolicy> List<T>.validateTimeTableOccupancyId(
        target: String?,
    ) = firstOrNull { !it.validate(target ?: "") }
        ?.let {
            throw InvalidTimeTableOccupancyIdException(it.reason)
        }

    private fun validateTimeTableOccupancyId(timetableId: String) {
        timeTableOccupancyIdPolicies.validateTimeTableOccupancyId(timetableId)
    }

    private fun validateTimeTableId(occupancyId: String) {
        timeTableIdPolicies.validateTimeTableId(occupancyId)
    }

    private fun validate(
        timetableId: String,
        occupancyId: String,
    ) {
        validateTimeTableId(timetableId)
        validateTimeTableOccupancyId(occupancyId)
    }

    fun create(
        timetableId: String,
        occupancyId: String,
    ): TimeTableOccupiedDomainEvent {
        validate(timetableId, occupancyId)

        return TimeTableOccupiedDomainEvent(
            timeTableId = timetableId,
            timeTableOccupancyId = occupancyId,
        )
    }
}
