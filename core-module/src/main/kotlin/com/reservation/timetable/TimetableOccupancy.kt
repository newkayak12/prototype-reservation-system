package com.reservation.timetable

import com.reservation.enumeration.OccupyStatus
import com.reservation.enumeration.OccupyStatus.CONFIRMED
import com.reservation.enumeration.OccupyStatus.PENDING
import com.reservation.enumeration.OccupyStatus.UNOCCUPIED
import com.reservation.timetable.snapshot.TimetableOccupancySnapShot
import java.time.LocalDateTime

class TimetableOccupancy(
    val id: String? = null,
    val timeTableId: String,
    val userId: String,
) {
    /**
     * 좌석을 잡은 것과 사용자가 그 좌석을 쓰겠다고 확정한 것은 다른 사건이다.
     * 그래서 점유는 확정이 아니라 **임시 홀드**에서 시작한다.
     */
    var occupiedStatus: OccupyStatus = PENDING
        protected set

    val occupiedDatetime: LocalDateTime = LocalDateTime.now()
    var unoccupiedDatetime: LocalDateTime? = null
        protected set

    fun confirm() {
        occupiedStatus = CONFIRMED
    }

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
