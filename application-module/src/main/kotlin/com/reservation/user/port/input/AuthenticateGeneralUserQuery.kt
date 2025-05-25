package com.reservation.user.port.input

import com.reservation.enumeration.Role
import com.reservation.user.port.output.AuthenticateGeneralUser

interface AuthenticateGeneralUserQuery {
    fun execute(request: GeneralUserQueryDto): AuthenticateGeneralUserQueryResult

    data class GeneralUserQueryDto(
        val loginId: String,
        val password: String,
    ) {
        val role = Role.USER

        fun toInquiry(): AuthenticateGeneralUser.AuthenticateGeneralUserInquiry {
            return AuthenticateGeneralUser.AuthenticateGeneralUserInquiry(
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
