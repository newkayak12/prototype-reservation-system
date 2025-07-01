package com.reservation.rest.user.general.response

import com.reservation.authenticate.port.input.AuthenticateGeneralUserQuery.AuthenticateGeneralUserQueryResult

data class LoginGeneralUserResponse(
    val accessToken: String,
) {
    companion object {
        fun from(result: AuthenticateGeneralUserQueryResult): LoginGeneralUserResponse {
            return LoginGeneralUserResponse(result.accessToken)
        }
    }
}
