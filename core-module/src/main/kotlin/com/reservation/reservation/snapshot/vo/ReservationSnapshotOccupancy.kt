package com.reservation.reservation.snapshot.vo

import java.time.LocalDateTime

class ReservationSnapshotOccupancy(
    val timeTableOccupancyId: String,
    val occupiedDatetime: LocalDateTime,
)
