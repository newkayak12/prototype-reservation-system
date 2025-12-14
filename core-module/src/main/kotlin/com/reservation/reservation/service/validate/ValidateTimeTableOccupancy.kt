package com.reservation.reservation.service.validate

import com.reservation.reservation.exceptions.ReservationTimeTableOccupancyIdException
import com.reservation.reservation.policy.validations.ReservationTimeTableOccupancyIdEmptyValidationPolicy
import com.reservation.reservation.policy.validations.ReservationTimeTableOccupancyIdFormatValidationPolicy
import com.reservation.reservation.policy.validations.ReservationTimeTableOccupancyIdPolicy

class ValidateTimeTableOccupancy {
    val timeTableOccupancyIdValidationPolicy: List<ReservationTimeTableOccupancyIdPolicy> =
        listOf(
            ReservationTimeTableOccupancyIdEmptyValidationPolicy(),
            ReservationTimeTableOccupancyIdFormatValidationPolicy(),
        )

    private fun List<ReservationTimeTableOccupancyIdPolicy>.validate(target: String?) =
        firstOrNull { !it.validate(target ?: "") }
            ?.let { throw ReservationTimeTableOccupancyIdException(it.reason) }

    fun validate(timeTableOccupancyId: String?) =
        timeTableOccupancyIdValidationPolicy.validate(timeTableOccupancyId)
}
