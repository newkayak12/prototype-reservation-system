package com.reservation.timetable.port.input.query.response

import com.reservation.enumeration.TableStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class FindTimeTableQueryResult(
    val id: String,
    val restaurantId: String,
    val date: LocalDate,
    val day: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val tableNumber: Int,
    val tableSize: Int,
    val tableStatus: TableStatus,
)
