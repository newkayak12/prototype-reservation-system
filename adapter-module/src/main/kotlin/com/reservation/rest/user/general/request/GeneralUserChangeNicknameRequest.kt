package com.reservation.rest.user.general.request

import com.reservation.user.self.port.input.ChangeGeneralUserNicknameCommand.ChangeGeneralUserNicknameCommandDto

data class GeneralUserChangeNicknameRequest(
    val nickname: String,
) {
    fun toCommand(id: String): ChangeGeneralUserNicknameCommandDto =
        ChangeGeneralUserNicknameCommandDto(id, nickname)
}
