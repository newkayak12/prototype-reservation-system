package com.reservation.reservation.vo

import com.reservation.reservation.snapshot.vo.ReservationSnapshotBooker

class ReservationBooker(
    val userId: String,
) {
    fun toSnapshot() = ReservationSnapshotBooker(userId)
}
