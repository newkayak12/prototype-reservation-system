package com.reservation.reservation.vo

class ReservationBooker(
    val userId: String,
) {
    fun toSnapshot() = ReservationSnapshotBooker(userId)
}
