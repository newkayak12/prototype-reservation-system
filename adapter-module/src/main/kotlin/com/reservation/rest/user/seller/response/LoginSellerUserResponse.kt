package com.reservation.rest.user.seller.response

import com.reservation.authenticate.port.input.AuthenticateSellerUserQuery.AuthenticateSellerUserQueryResult

data class LoginSellerUserResponse(
    val accessToken: String,
) {
    companion object {
        fun from(result: AuthenticateSellerUserQueryResult): LoginSellerUserResponse {
            return LoginSellerUserResponse(result.accessToken)
        }
    }
}
