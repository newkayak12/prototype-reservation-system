package com.reservation.rest.timetable.request

import com.reservation.enumeration.TableStatus
import com.reservation.enumeration.TableStatus.EMPTY
import com.reservation.timetable.port.input.query.request.FindTimeTableQuery
import java.time.LocalDate

data class FindTimeTableRequest(
    val restaurantId: String,
    val date: LocalDate,
    val tableStatus: TableStatus = EMPTY,
) {
    fun toQuery(): FindTimeTableQuery =
        FindTimeTableQuery(
            restaurantId = restaurantId,
            date = date,
            tableStatus = tableStatus,
        )
}
