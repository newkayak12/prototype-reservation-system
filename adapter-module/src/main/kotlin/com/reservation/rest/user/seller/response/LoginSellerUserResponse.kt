package com.reservation.rest.user.seller.response

import com.reservation.authenticate.port.input.query.response.AuthenticateSellerUserQueryResult

data class LoginSellerUserResponse(
    val accessToken: String,
) {
    companion object {
        fun from(result: AuthenticateSellerUserQueryResult): LoginSellerUserResponse {
            return LoginSellerUserResponse(result.accessToken)
        }
    }
}
