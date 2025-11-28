package com.reservation.timetable.snapshot

import com.reservation.enumeration.OccupyStatus
import java.time.LocalDateTime

data class TimetableOccupancySnapShot(
    val id: String? = null,
    val timeTableId: String,
    val userId: String,
    val occupiedStatus: OccupyStatus,
    val occupiedDatetime: LocalDateTime,
    val unoccupiedDatetime: LocalDateTime? = null,
)
