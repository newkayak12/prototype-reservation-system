package com.reservation.schedule.port.input

import com.reservation.schedule.port.input.command.request.CreateHolidayCommand

interface CreateHolidayUseCase {
    fun execute(command: CreateHolidayCommand): Boolean
}
