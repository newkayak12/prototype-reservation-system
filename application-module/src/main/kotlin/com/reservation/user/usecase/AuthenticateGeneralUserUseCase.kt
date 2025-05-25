package com.reservation.user.usecase

import com.reservation.authenticate.service.AuthenticateSignInService
import com.reservation.config.annotations.UseCase
import com.reservation.enumeration.JWTType
import com.reservation.jwt.provider.JWTRecord
import com.reservation.jwt.provider.TokenProvider
import com.reservation.user.port.input.AuthenticateGeneralUserQuery
import com.reservation.user.port.input.AuthenticateGeneralUserQuery.AuthenticateGeneralUserQueryResult
import com.reservation.user.port.input.AuthenticateGeneralUserQuery.GeneralUserQueryDto
import com.reservation.user.port.output.AuthenticateGeneralUser

@UseCase
class AuthenticateGeneralUserUseCase(
    val authenticateSignInService: AuthenticateSignInService,
    val authenticateGeneralUser: AuthenticateGeneralUser,
    val tokenProvider: TokenProvider<JWTRecord>,
) : AuthenticateGeneralUserQuery {
    override fun execute(request: GeneralUserQueryDto): AuthenticateGeneralUserQueryResult {
        val authenticate = authenticateGeneralUser.query(request.toInquiry()).toDomain()
        val authenticated = authenticateSignInService.signIn(authenticate, request.password)

        val record =
            JWTRecord(
                authenticated.id,
                authenticated.loginId(),
                authenticated.role,
            )
        val accessToken = tokenProvider.tokenize(record)
        val refreshToken = tokenProvider.tokenize(record, JWTType.REFRESH_TOKEN)
        return AuthenticateGeneralUserQueryResult(accessToken, refreshToken)
    }
}
