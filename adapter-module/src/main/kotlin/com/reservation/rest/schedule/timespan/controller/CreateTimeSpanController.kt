package com.reservation.rest.schedule.timespan.controller

import com.reservation.rest.common.response.BooleanResponse
import com.reservation.rest.schedule.timespan.TimeSpanUrl
import com.reservation.rest.schedule.timespan.request.CreateTimeSpanRequest
import com.reservation.schedule.port.input.CreateTimeSpanUseCase
import com.reservation.schedule.port.input.command.request.CreateTimeSpanCommand
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class CreateTimeSpanController(
    private val createTimeSpanUseCase: CreateTimeSpanUseCase,
) {

    @PostMapping(TimeSpanUrl.CREATE)
    fun createTimeSpan(
        @PathVariable(name = "id") id: String,
        @RequestBody @Valid createTimeSpanRequest: CreateTimeSpanRequest,
    ): BooleanResponse = BooleanResponse.created(
        createTimeSpanUseCase.execute(
            CreateTimeSpanCommand(
                restaurantId = id,
                day = createTimeSpanRequest.day,
                startTime = createTimeSpanRequest.startTime,
                endTime = createTimeSpanRequest.endTime,
            )
        )
    )
}
