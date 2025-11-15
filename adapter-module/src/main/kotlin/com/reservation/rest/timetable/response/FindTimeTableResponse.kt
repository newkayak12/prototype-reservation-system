package com.reservation.rest.timetable.response

import com.reservation.enumeration.TableStatus
import com.reservation.timetable.port.input.query.response.FindTimeTableQueryResult
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class FindTimeTableResponse(
    val id: String,
    val restaurantId: String,
    val date: LocalDate,
    val day: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val tableNumber: Int,
    val tableSize: Int,
    val tableStatus: TableStatus,
) {
    companion object {
        fun from(result: FindTimeTableQueryResult): FindTimeTableResponse =
            FindTimeTableResponse(
                id = result.id,
                restaurantId = result.restaurantId,
                date = result.date,
                day = result.day,
                startTime = result.startTime,
                endTime = result.endTime,
                tableNumber = result.tableNumber,
                tableSize = result.tableSize,
                tableStatus = result.tableStatus,
            )
    }
}
