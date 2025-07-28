package com.reservation.authenticate.port.input.query.request

import com.reservation.authenticate.port.output.AuthenticateGeneralUser.AuthenticateGeneralUserInquiry

data class GeneralUserQuery(
    val loginId: String,
    val password: String,
) {
    fun toInquiry(): AuthenticateGeneralUserInquiry {
        return AuthenticateGeneralUserInquiry(
            loginId,
            password,
        )
    }
}
