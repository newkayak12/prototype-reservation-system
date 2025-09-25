package com.reservation.menu.port.input

import com.reservation.menu.port.input.request.UpdateMenuCommand

interface ChangeMenuUseCase {
    fun execute(command: UpdateMenuCommand): Boolean
}
