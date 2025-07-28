package com.reservation.user.self.port.input.command.request

data class ChangeGeneralUserPasswordCommand(
    val id: String,
    val encodedPassword: String,
)
