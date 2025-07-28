package com.reservation.rest.user.general.request

import com.reservation.user.self.port.input.command.request.ChangeGeneralUserNicknameCommand

data class GeneralUserChangeNicknameRequest(
    val nickname: String,
) {
    fun toCommand(id: String): ChangeGeneralUserNicknameCommand =
        ChangeGeneralUserNicknameCommand(id, nickname)
}
