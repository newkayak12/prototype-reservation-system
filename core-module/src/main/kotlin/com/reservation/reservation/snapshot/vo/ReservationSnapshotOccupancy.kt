package com.reservation.reservation.vo

import java.time.LocalDateTime

class ReservationSnapshotOccupancy(
    val timeTableOccupancyId: String,
    val occupiedDatetime: LocalDateTime,
)
