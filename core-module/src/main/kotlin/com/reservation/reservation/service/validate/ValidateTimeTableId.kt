package com.reservation.reservation.service.validate

import com.reservation.reservation.exceptions.ReservationTimeTableIdException
import com.reservation.reservation.policy.validations.ReservationTimeTableIdEmptyValidationPolicy
import com.reservation.reservation.policy.validations.ReservationTimeTableIdFormatValidationPolicy
import com.reservation.reservation.policy.validations.ReservationTimeTableIdPolicy

class ValidateTimeTableId {
    val timeTableIdValidationPolicy: List<ReservationTimeTableIdPolicy> =
        listOf(
            ReservationTimeTableIdEmptyValidationPolicy(),
            ReservationTimeTableIdFormatValidationPolicy(),
        )

    private fun List<ReservationTimeTableIdPolicy>.validate(target: String?) =
        firstOrNull { !it.validate(target ?: "") }
            ?.let { throw ReservationTimeTableIdException(it.reason) }

    fun validate(timeTableId: String?) = timeTableIdValidationPolicy.validate(timeTableId)
}
