package com.reservation.schedule.port.input

import com.reservation.schedule.port.input.command.request.CreateScheduleCommand

interface CreateScheduleUseCase {
    fun execute(command: CreateScheduleCommand): Boolean
}
