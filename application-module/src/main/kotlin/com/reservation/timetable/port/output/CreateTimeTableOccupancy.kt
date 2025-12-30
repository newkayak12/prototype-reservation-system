package com.reservation.timetable.port.output

import com.reservation.enumeration.OccupyStatus
import com.reservation.enumeration.TableStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

interface CreateTimeTableOccupancy {
    fun createTimeTableOccupancy(inquiry: CreateTimeTableOccupancyInquiry): String?

    data class CreateTimeTableOccupancyInquiry(
        val id: String,
        val restaurantId: String,
        val userId: String,
        val date: LocalDate,
        val day: DayOfWeek,
        val startTime: LocalTime,
        val endTime: LocalTime,
        val tableNumber: Int,
        val tableSize: Int,
        val tableStatus: TableStatus,
        val timetableOccupancy: TimetableOccupancyInquiry,
    )

    data class TimetableOccupancyInquiry(
        val id: String? = null,
        val timeTableId: String,
        val userId: String,
        val occupiedStatus: OccupyStatus,
        val occupiedDatetime: LocalDateTime,
        val unoccupiedDatetime: LocalDateTime? = null,
    )
}
