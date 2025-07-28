package com.reservation.user.resign.port.input

interface ResignUserUseCase {
    fun execute(id: String): Boolean
}
