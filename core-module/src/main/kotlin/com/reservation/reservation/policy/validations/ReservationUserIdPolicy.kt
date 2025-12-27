package com.reservation.reservation.policy.validations

interface ReservationUserIdPolicy {
    val reason: String

    fun validate(target: String): Boolean
}
