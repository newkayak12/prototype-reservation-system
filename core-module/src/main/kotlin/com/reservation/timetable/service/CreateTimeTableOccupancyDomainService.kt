package com.reservation.timetable.service

import com.reservation.enumeration.TableStatus
import com.reservation.timetable.TimeTable
import com.reservation.timetable.policy.exceptions.InvalidTimeTableIdException
import com.reservation.timetable.policy.exceptions.InvalidTimeTableStatusException
import com.reservation.timetable.policy.exceptions.InvalidTimeTableUserIdException
import com.reservation.timetable.policy.validation.TimeTableIdFormatValidationPolicy
import com.reservation.timetable.policy.validation.TimeTableIdIsNotEmptyValidationPolicy
import com.reservation.timetable.policy.validation.TimeTableIdPolicy
import com.reservation.timetable.policy.validation.TimeTableStatusIsNotVacantPolicy
import com.reservation.timetable.policy.validation.TimeTableStatusPolicy
import com.reservation.timetable.policy.validation.UserIdFormatValidationPolicy
import com.reservation.timetable.policy.validation.UserIdIsNotEmptyValidationPolicy
import com.reservation.timetable.policy.validation.UserIdPolicy
import com.reservation.timetable.snapshot.TimeTableSnapshot

class CreateTimeTableOccupancyDomainService {
    private val timeTableIdPolicies =
        listOf(
            TimeTableIdIsNotEmptyValidationPolicy(),
            TimeTableIdFormatValidationPolicy(),
        )
    private val userIdPolicies =
        listOf(
            UserIdIsNotEmptyValidationPolicy(),
            UserIdFormatValidationPolicy(),
        )

    private val timeTableStatusPolicies =
        listOf(
            TimeTableStatusIsNotVacantPolicy(),
        )

    private fun <T : TimeTableIdPolicy> List<T>.validateTimetableId(target: String?) =
        firstOrNull { !it.validate(target ?: "") }
            ?.let {
                throw InvalidTimeTableIdException(it.reason)
            }

    private fun <T : UserIdPolicy> List<T>.validateUserId(target: String) =
        firstOrNull { !it.validate(target) }
            ?.let {
                throw InvalidTimeTableUserIdException(it.reason)
            }

    private fun <T : TimeTableStatusPolicy> List<T>.validateTimeTableStatus(target: TableStatus) =
        firstOrNull { !it.validate(target) }
            ?.let {
                throw InvalidTimeTableStatusException(it.reason)
            }

    private fun validateTimetableId(timeTableId: String?) =
        timeTableIdPolicies.validateTimetableId(timeTableId)

    private fun validateUserId(userId: String) = userIdPolicies.validateUserId(userId)

    private fun validateTimeTableStatus(target: TableStatus) =
        timeTableStatusPolicies.validateTimeTableStatus(target)

    private fun validate(
        userId: String,
        timeTable: TimeTable,
    ) {
        validateUserId(userId)
        validateTimetableId(timeTable.id)
        validateTimeTableStatus(timeTable.getStatus)
    }

    fun create(
        userId: String,
        timeTable: TimeTable,
    ): TimeTableSnapshot {
        validate(userId, timeTable)

        timeTable.attachOccupied(userId)
        return timeTable.toSnapshot()
    }
}
