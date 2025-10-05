package com.reservation.rest.schedule.holiday.controller

import com.reservation.rest.common.response.BooleanResponse
import com.reservation.rest.schedule.holiday.HolidayUrl
import com.reservation.rest.schedule.holiday.request.CreateHolidayRequest
import com.reservation.schedule.port.input.CreateHolidayUseCase
import com.reservation.schedule.port.input.command.request.CreateHolidayCommand
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class CreateHolidayController(
    private val createHolidayUseCase: CreateHolidayUseCase,
) {

    @PostMapping(HolidayUrl.CREATE)
    fun createHoliday(
        @PathVariable(name = "id") id: String,
        @RequestBody @Valid createHolidayRequest: CreateHolidayRequest,
    ): BooleanResponse = BooleanResponse.created(
        createHolidayUseCase.execute(CreateHolidayCommand(id, createHolidayRequest.date))
    )
}
