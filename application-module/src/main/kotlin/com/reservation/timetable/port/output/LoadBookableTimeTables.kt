package com.reservation.timetable.port.output

import com.reservation.timetable.TimeTable
import java.time.LocalDate
import java.time.LocalTime

interface LoadBookableTimeTables {
    fun query(inquiry: LoadBookableTimeTablesInquiry): List<TimeTable>

    data class LoadBookableTimeTablesInquiry(
        val restaurantId: String,
        val date: LocalDate,
        val startTime: LocalTime,
    )
}
