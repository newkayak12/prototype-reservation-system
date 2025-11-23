package com.reservation.rest.timetable.occupancy.create

import com.reservation.authenticate.port.input.ExtractIdentifierFromHeaderUseCase
import com.reservation.rest.common.response.BooleanResponse
import com.reservation.rest.timetable.TimeTableOccupyUrl
import com.reservation.rest.timetable.request.CreateTimeTableOccupancyRequest
import com.reservation.timetable.port.input.CreateTimeTableOccupancyUseCase
import com.reservation.timetable.port.input.command.request.CreateTimeTableOccupancyCommand
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class CreateTimeTableOccupancyController(
    private val createTimeTableOccupancyUseCase: CreateTimeTableOccupancyUseCase,
    private val extractIdentifierFromHeaderUseCase: ExtractIdentifierFromHeaderUseCase,
) {
    @PostMapping(TimeTableOccupyUrl.BOOKING)
    fun createTimeTableOccupancy(
        header: HttpHeaders,
        @PathVariable("restaurantId") restaurantId: String,
        @RequestBody request: CreateTimeTableOccupancyRequest,
    ): BooleanResponse {
        val userId =
            extractIdentifierFromHeaderUseCase.execute(
                header.getFirst(HttpHeaders.AUTHORIZATION),
            )

        val command =
            CreateTimeTableOccupancyCommand(
                userId = userId,
                restaurantId = restaurantId,
                date = request.date,
                startTime = request.startTime,
            )

        return BooleanResponse.created(createTimeTableOccupancyUseCase.execute(command))
    }
}
