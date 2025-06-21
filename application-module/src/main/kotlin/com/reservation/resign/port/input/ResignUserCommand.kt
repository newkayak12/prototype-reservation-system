package com.reservation.resign.port.input

@FunctionalInterface
interface ResignUserCommand {
    fun execute(id: String): Boolean
}
