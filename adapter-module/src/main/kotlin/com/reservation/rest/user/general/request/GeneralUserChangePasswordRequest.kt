package com.reservation.rest.user.general.request

import com.reservation.user.self.port.input.command.request.ChangeGeneralUserPasswordCommand

data class GeneralUserChangePasswordRequest(
//    @field:NotEmpty
    val encodedPassword: String,
) {
    fun toCommand(id: String): ChangeGeneralUserPasswordCommand =
        ChangeGeneralUserPasswordCommand(id, encodedPassword)
}
