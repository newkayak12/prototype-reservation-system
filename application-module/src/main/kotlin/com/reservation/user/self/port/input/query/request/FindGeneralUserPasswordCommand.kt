package com.reservation.user.self.port.input.query.request

import com.reservation.user.self.port.output.LoadGeneralUserByLoginIdAndEmail.LoadGeneralUserByLoginIdAndEmailInquiry

data class FindGeneralUserPasswordCommand(
    val loginId: String,
    val email: String,
) {
    fun toInquiry(): LoadGeneralUserByLoginIdAndEmailInquiry =
        LoadGeneralUserByLoginIdAndEmailInquiry(loginId, email)
}
