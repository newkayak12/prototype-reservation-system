package com.reservation.rest.user.general.request

import com.reservation.user.self.port.input.FindGeneralUserPasswordCommand.FindGeneralUserPasswordCommandDto

data class FindGeneralUserPasswordRequest(
    val loginId: String,
    val email: String,
) {
    fun toCommand(): FindGeneralUserPasswordCommandDto =
        FindGeneralUserPasswordCommandDto(loginId, email)
}
