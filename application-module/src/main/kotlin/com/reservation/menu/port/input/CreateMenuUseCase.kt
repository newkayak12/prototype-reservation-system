package com.reservation.menu.port.input

import com.reservation.menu.port.input.request.CreateMenuCommand

interface CreateMenuUseCase {
    fun execute(command: CreateMenuCommand): Boolean
}
