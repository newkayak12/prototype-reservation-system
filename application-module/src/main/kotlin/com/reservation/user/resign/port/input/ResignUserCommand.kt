package com.reservation.user.resign.port.input

fun interface ResignUserCommand {
    fun execute(id: String): Boolean
}
