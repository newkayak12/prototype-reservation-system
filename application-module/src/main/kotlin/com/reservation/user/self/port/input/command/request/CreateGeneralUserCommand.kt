package com.reservation.user.self.port.input.command.request

data class CreateGeneralUserCommand(
    val loginId: String,
    val password: String,
    val email: String,
    val mobile: String,
    val nickname: String,
)
