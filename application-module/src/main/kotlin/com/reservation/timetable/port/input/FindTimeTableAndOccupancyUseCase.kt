package com.reservation.timetable.port.input

import com.reservation.timetable.port.input.query.response.FindTimeTableAndOccupancyQueryResult

interface FindTimeTableAndOccupancyUseCase {
    fun execute(
        timeTableId: String,
        timeTableOccupancyId: String,
    ): FindTimeTableAndOccupancyQueryResult
}
