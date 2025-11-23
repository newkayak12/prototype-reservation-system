package com.reservation.timetable.port.input

import com.reservation.timetable.port.input.command.request.CreateTimeTableOccupancyCommand

interface CreateTimeTableOccupancyUseCase {
    fun execute(command: CreateTimeTableOccupancyCommand): Boolean
}
