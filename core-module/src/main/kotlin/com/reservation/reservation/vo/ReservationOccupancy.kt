package com.reservation.reservation.vo

import com.reservation.reservation.snapshot.vo.ReservationSnapshotOccupancy
import java.time.LocalDateTime

class ReservationOccupancy(
    val timeTableOccupancyId: String,
    val occupiedDatetime: LocalDateTime,
) {
    fun toSnapshot() =
        ReservationSnapshotOccupancy(
            timeTableOccupancyId = timeTableOccupancyId,
            occupiedDatetime = occupiedDatetime,
        )
}
