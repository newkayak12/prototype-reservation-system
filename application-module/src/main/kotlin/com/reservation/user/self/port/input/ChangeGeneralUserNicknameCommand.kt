package com.reservation.user.self.port.input

import com.reservation.enumeration.Role

interface ChangeGeneralUserNicknameCommand {
    fun execute(command: ChangeGeneralUserNicknameCommandDto): Boolean

    data class ChangeGeneralUserNicknameCommandDto(
        val id: String,
        val nickname: String,
    ) {
        val role = Role.USER
    }
}
