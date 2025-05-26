package com.reservation.rest.user.general.response

import com.reservation.user.self.port.input.AuthenticateGeneralUserQuery.AuthenticateGeneralUserQueryResult

data class GeneralUserLoginResponse(
    val accessToken: String,
    val refreshToken: String,
) {
    companion object {
        fun from(result: AuthenticateGeneralUserQueryResult): GeneralUserLoginResponse {
            return GeneralUserLoginResponse(result.accessToken, result.accessToken)
        }
    }
}
