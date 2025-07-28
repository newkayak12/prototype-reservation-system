package com.reservation.authenticate.port.input.query.request

import com.reservation.authenticate.port.output.AuthenticateSellerUser.AuthenticateSellerUserInquiry

data class SellerUserQuery(
    val loginId: String,
    val password: String,
) {
    fun toInquiry(): AuthenticateSellerUserInquiry {
        return AuthenticateSellerUserInquiry(
            loginId,
            password,
        )
    }
}
