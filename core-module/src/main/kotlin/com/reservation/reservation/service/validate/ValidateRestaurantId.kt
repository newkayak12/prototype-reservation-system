package com.reservation.reservation.service.validate

import com.reservation.reservation.exceptions.ReservationRestaurantIdException
import com.reservation.reservation.policy.validations.ReservationRestaurantIdEmptyValidationPolicy
import com.reservation.reservation.policy.validations.ReservationRestaurantIdFormatValidationPolicy
import com.reservation.reservation.policy.validations.ReservationRestaurantIdPolicy

class ValidateRestaurantId {
    val restaurantIdValidationPolicy: List<ReservationRestaurantIdPolicy> =
        listOf(
            ReservationRestaurantIdEmptyValidationPolicy(),
            ReservationRestaurantIdFormatValidationPolicy(),
        )

    private fun List<ReservationRestaurantIdPolicy>.validate(target: String?) =
        firstOrNull { !it.validate(target ?: "") }
            ?.let { throw ReservationRestaurantIdException(it.reason) }

    fun validate(restaurantId: String?) = restaurantIdValidationPolicy.validate(restaurantId)
}
