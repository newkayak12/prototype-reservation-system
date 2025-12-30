package com.reservation.reservation.port.input

import com.reservation.reservation.port.input.command.CreateReservationCommand

interface CreateReservationUseCase {
    fun execute(command: CreateReservationCommand): Boolean
}
