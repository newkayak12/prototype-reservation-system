package com.reservation.rest.internal.timetable.find

import com.reservation.rest.internal.timetable.TimeTableOccupancyUrl
import com.reservation.rest.internal.timetable.response.FindTimeTableOccupancyInternallyResponse
import com.reservation.rest.internal.timetable.response.FindTimeTableOccupancyInternallyResponse.FindTimeTableOccupancyBookingInformationResponse
import com.reservation.rest.internal.timetable.response.FindTimeTableOccupancyInternallyResponse.FindTimeTableOccupancyTableInformationResponse
import com.reservation.timetable.port.input.FindTimeTableAndOccupancyUseCase
import com.reservation.timetable.port.input.query.response.FindTimeTableAndOccupancyQueryResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class FindTimeTableOccupancyInternalController(
    private val findTimeTableAndOccupancyUseCase: FindTimeTableAndOccupancyUseCase,
) {
    @GetMapping(TimeTableOccupancyUrl.FIND_INTERNAL)
    fun findTimeTableOccupancyInternally(
        @PathVariable(name = "timeTableId") timeTableId: String,
        @PathVariable(name = "timeTableOccupancyId") timeTableOccupancyId: String,
    ): FindTimeTableOccupancyInternallyResponse =
        findTimeTableAndOccupancyUseCase
            .execute(timeTableId, timeTableOccupancyId)
            .toResponse()

    private fun FindTimeTableAndOccupancyQueryResult.toResponse() =
        FindTimeTableOccupancyInternallyResponse(
            FindTimeTableOccupancyTableInformationResponse(
                timeTableId = timeTableId,
                restaurantId = restaurantId,
                date = date,
                day = day,
                endTime = endTime,
                tableNumber = tableNumber,
                tableSize = tableSize,
            ),
            FindTimeTableOccupancyBookingInformationResponse(
                timeTableOccupancyId = timeTableOccupancyId,
                userId = userId,
                occupiedDatetime = occupiedDatetime,
            ),
        )
}
