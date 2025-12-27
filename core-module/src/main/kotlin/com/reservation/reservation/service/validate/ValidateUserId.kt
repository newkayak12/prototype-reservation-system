package com.reservation.reservation.service.validate

import com.reservation.reservation.exceptions.ReservationUserIdException
import com.reservation.reservation.policy.validations.ReservationUserIdEmptyValidationPolicy
import com.reservation.reservation.policy.validations.ReservationUserIdFormatValidationPolicy
import com.reservation.reservation.policy.validations.ReservationUserIdPolicy

class ValidateUserId {
    val userIdValidationPolicy: List<ReservationUserIdPolicy> =
        listOf(
            ReservationUserIdEmptyValidationPolicy(),
            ReservationUserIdFormatValidationPolicy(),
        )

    private fun List<ReservationUserIdPolicy>.validate(target: String?) =
        firstOrNull { !it.validate(target ?: "") }
            ?.let { throw ReservationUserIdException(it.reason) }

    fun validate(userId: String?) = userIdValidationPolicy.validate(userId)
}
