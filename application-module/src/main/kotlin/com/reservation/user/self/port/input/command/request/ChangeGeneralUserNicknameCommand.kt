package com.reservation.user.self.port.input.command.request

import com.reservation.enumeration.Role

data class ChangeGeneralUserNicknameCommand(
    val id: String,
    val nickname: String,
    val role: Role = Role.USER,
)
