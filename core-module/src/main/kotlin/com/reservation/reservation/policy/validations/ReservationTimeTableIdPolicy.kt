package com.reservation.reservation.policy.validations

interface ReservationTimeTableIdPolicy {
    val reason: String

    fun validate(target: String): Boolean
}
