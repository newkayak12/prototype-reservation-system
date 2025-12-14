package com.reservation.reservation.vo

import com.reservation.reservation.snapshot.vo.ReservationSnapshotSchedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class ReservationSchedule(
    val timeTableId: String,
    val date: LocalDate,
    val day: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
) {
    fun toSnapshot() =
        ReservationSnapshotSchedule(
            timeTableId = timeTableId,
            date = date,
            day = day,
            startTime = startTime,
            endTime = endTime,
        )
}
