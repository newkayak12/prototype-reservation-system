package com.reservation.rest.user.general.request

import com.reservation.user.self.port.input.query.request.FindGeneralUserPasswordCommand

data class FindGeneralUserPasswordRequest(
    val loginId: String,
    val email: String,
) {
    fun toCommand(): FindGeneralUserPasswordCommand = FindGeneralUserPasswordCommand(loginId, email)
}
