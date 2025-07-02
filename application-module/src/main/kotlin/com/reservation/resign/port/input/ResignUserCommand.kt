package com.reservation.resign.port.input

fun interface ResignUserCommand {
    fun execute(id: String): Boolean
}
