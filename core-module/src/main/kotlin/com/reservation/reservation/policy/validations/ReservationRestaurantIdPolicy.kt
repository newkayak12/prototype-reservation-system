package com.reservation.reservation.policy.validations

interface ReservationRestaurantIdPolicy {
    val reason: String

    fun validate(target: String): Boolean
}
