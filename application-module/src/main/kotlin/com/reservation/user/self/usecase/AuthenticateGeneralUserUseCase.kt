package com.reservation.user.self.usecase

import com.reservation.authenticate.service.AuthenticateSignInService
import com.reservation.config.annotations.UseCase
import com.reservation.enumeration.JWTType
import com.reservation.jwt.provider.JWTRecord
import com.reservation.jwt.provider.TokenProvider
import com.reservation.user.exceptions.WrongLoginIdOrPasswordException
import com.reservation.user.history.access.port.input.CreateUserAccessHistoriesCommand
import com.reservation.user.history.access.port.input.CreateUserAccessHistoriesCommand.CreateUserHistoryCommandDto
import com.reservation.user.self.port.input.AuthenticateGeneralUserQuery
import com.reservation.user.self.port.input.AuthenticateGeneralUserQuery.AuthenticateGeneralUserQueryResult
import com.reservation.user.self.port.input.AuthenticateGeneralUserQuery.GeneralUserQueryDto
import com.reservation.user.self.port.output.AuthenticateGeneralUser
import com.reservation.user.self.port.output.UpdateAuthenticateResult
import com.reservation.user.self.port.output.UpdateAuthenticateResult.UpdateAuthenticateResultDto
import org.springframework.transaction.annotation.Transactional

@UseCase
class AuthenticateGeneralUserUseCase(
    val authenticateSignInService: AuthenticateSignInService,
    val authenticateGeneralUser: AuthenticateGeneralUser,
    val createUserHistoriesCommand: CreateUserAccessHistoriesCommand,
    val updateAuthenticateResult: UpdateAuthenticateResult,
    val tokenProvider: TokenProvider<JWTRecord>,
) : AuthenticateGeneralUserQuery {
    @Transactional(noRollbackFor = [WrongLoginIdOrPasswordException::class])
    override fun execute(request: GeneralUserQueryDto): AuthenticateGeneralUserQueryResult {
        val authenticate =
            authenticateGeneralUser.query(request.toInquiry())?.toDomain()
                ?: run {
                    throw NoSuchElementException()
                }

        val authenticated = authenticateSignInService.signIn(authenticate, request.password)

        // histories 저장
        createUserHistoriesCommand.execute(
            authenticated.accessHistories().map {
                CreateUserHistoryCommandDto(
                    it.authenticateId,
                    it.loginId,
                    it.accessStatus(),
                    it.accessDateTime(),
                )
            },
        )

        if (!authenticated.isSuccess) {
            // authenticated.
            updateAuthenticateResult.save(
                UpdateAuthenticateResultDto(
                    authenticated.id,
                    authenticated.failCount,
                    authenticated.lockedDateTime,
                    authenticated.userStatus,
                ),
            )
            throw WrongThreadException()
        }

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
