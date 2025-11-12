package com.reservation.timetable.port.output

import com.reservation.enumeration.TableStatus
import com.reservation.enumeration.TableStatus.EMPTY
import com.reservation.timetable.port.input.query.response.FindTimeTableQueryResult
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

interface FindTimeTables {

    fun query(inquiry: FindTimeTableInquiry): List<FindTimeTableResult>

    data class FindTimeTableInquiry(
        val restaurantId: String,
        val date: LocalDate,
        val tableStatus: TableStatus = EMPTY,
    )

    data class FindTimeTableResult(
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
        fun toQuery(): FindTimeTableQueryResult =
            FindTimeTableQueryResult(
                id = id,
                restaurantId = restaurantId,
                date = date,
                day = day,
                startTime = startTime,
                endTime = endTime,
                tableNumber = tableNumber,
                tableSize = tableSize,
                tableStatus = tableStatus,
            )
    }


}
