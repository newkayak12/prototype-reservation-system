package com.reservation.user.self.port.input

import com.reservation.user.self.port.output.AuthenticateGeneralUser.AuthenticateGeneralUserInquiry

@FunctionalInterface
interface AuthenticateGeneralUserQuery {
    fun execute(request: GeneralUserQueryDto): AuthenticateGeneralUserQueryResult

    data class GeneralUserQueryDto(
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

    data class AuthenticateGeneralUserQueryResult(
        val accessToken: String,
        val refreshToken: String,
    )
}
