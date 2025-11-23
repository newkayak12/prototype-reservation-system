package com.reservation.timetable.snapshot

import com.reservation.enumeration.TableStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Suppress("LongParameterList")
data class TimeTableSnapshot(
    val id: String? = null,
    val restaurantId: String,
    val date: LocalDate,
    val day: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val tableNumber: Int,
    val tableSize: Int,
    val tableStatus: TableStatus,
    val timetableOccupancy: TimetableOccupancySnapShot?,
)
