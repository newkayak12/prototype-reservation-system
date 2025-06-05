package com.reservation.rest.user.general.request

import com.reservation.user.self.port.input.ChangeGeneralUserPasswordCommand.ChangeGeneralUserPasswordCommandDto

data class GeneralUserChangePasswordRequest(
//    @field:NotEmpty
    val encodedPassword: String,
) {
    fun toCommand(id: String): ChangeGeneralUserPasswordCommandDto =
        ChangeGeneralUserPasswordCommandDto(id, encodedPassword)
}
