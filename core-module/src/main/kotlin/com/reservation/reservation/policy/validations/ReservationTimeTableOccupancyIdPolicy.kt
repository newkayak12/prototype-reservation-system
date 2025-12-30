package com.reservation.reservation.policy.validations

interface ReservationTimeTableOccupancyIdPolicy {
    val reason: String

    fun validate(target: String): Boolean
}
