package com.reservation.reservation.port.output

import com.reservation.enumeration.ReservationStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

interface CreateReservation {
    fun command(inquiry: CreateReservationInquiry): Boolean

    data class CreateReservationInquiry(
        val restaurantId: String,
        val userId: String,
        val timeTableId: String,
        val timeTableOccupancyId: String,
        val date: LocalDate,
        val day: DayOfWeek,
        val startTime: LocalTime,
        val endTime: LocalTime,
        val tableNumber: Int,
        val tableSize: Int,
        val occupiedDatetime: LocalDateTime,
        val reservationStatus: ReservationStatus,
    )
}
