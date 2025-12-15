package com.reservation.reservation.snapshot.vo

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class ReservationSnapshotSchedule(
    val timeTableId: String,
    val date: LocalDate,
    val day: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
)
