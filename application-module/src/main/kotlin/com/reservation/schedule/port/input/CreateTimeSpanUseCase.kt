package com.reservation.schedule.port.input

import com.reservation.schedule.port.input.command.request.CreateTimeSpanCommand

interface CreateTimeSpanUseCase {

    fun execute(command: CreateTimeSpanCommand): Boolean

}
