package com.reservation.timetable.port.input

import com.reservation.timetable.port.input.query.request.FindTimeTableQuery
import com.reservation.timetable.port.input.query.response.FindTimeTableQueryResult

interface FindTimeTableUseCase {
    fun execute(query: FindTimeTableQuery): List<FindTimeTableQueryResult>
}
