package com.reservation.timetable

import com.reservation.enumeration.OccupyStatus
import com.reservation.enumeration.OccupyStatus.OCCUPIED
import com.reservation.enumeration.OccupyStatus.UNOCCUPIED
import com.reservation.timetable.snapshot.TimetableOccupancySnapShot
import java.time.LocalDateTime

class TimetableOccupancy(
    val id: String? = null,
    val timeTableId: String,
    val userId: String,
) {
    var occupiedStatus: OccupyStatus = OCCUPIED
        protected set

    val occupiedDatetime: LocalDateTime = LocalDateTime.now()
    var unoccupiedDatetime: LocalDateTime? = null
        protected set

    fun unoccupied() {
        occupiedStatus = UNOCCUPIED
        unoccupiedDatetime = LocalDateTime.now()
    }

    fun toSnapshot(): TimetableOccupancySnapShot {
        return TimetableOccupancySnapShot(
            timeTableId = timeTableId,
            userId = userId,
            occupiedStatus = occupiedStatus,
            occupiedDatetime = occupiedDatetime,
            unoccupiedDatetime = unoccupiedDatetime,
        )
    }
}
