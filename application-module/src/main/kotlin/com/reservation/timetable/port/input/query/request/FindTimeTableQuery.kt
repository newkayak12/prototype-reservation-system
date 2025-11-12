package com.reservation.timetable.port.input.query.request

import com.reservation.enumeration.TableStatus
import com.reservation.enumeration.TableStatus.EMPTY
import com.reservation.timetable.port.output.FindTimeTables.FindTimeTableInquiry
import java.time.LocalDate

data class FindTimeTableQuery(
    val restaurantId: String,
    val date: LocalDate,
    val tableStatus: TableStatus = EMPTY,
) {
    fun toInquiry(): FindTimeTableInquiry =
        FindTimeTableInquiry(
            restaurantId = restaurantId,
            date = date,
            tableStatus = tableStatus,
        )
}
