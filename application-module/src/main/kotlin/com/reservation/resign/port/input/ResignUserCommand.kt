package com.reservation.resign.port.input

interface ResignUserCommand {
    fun execute(id: String): Boolean
}
