package com.reservation.user.self.usecase

import com.reservation.authenticate.AccessHistory
import com.reservation.authenticate.Authenticate
import com.reservation.authenticate.service.AuthenticateSignInService
import com.reservation.config.annotations.UseCase
import com.reservation.enumeration.JWTType
import com.reservation.user.exceptions.NoSuchDatabaseElementException
import com.reservation.user.exceptions.WrongLoginIdOrPasswordException
import com.reservation.user.history.access.port.input.CreateUserAccessHistoriesCommand
import com.reservation.user.history.access.port.input.CreateUserAccessHistoriesCommand.CreateUserHistoryCommandDto
import com.reservation.user.self.port.input.AuthenticateGeneralUserQuery
import com.reservation.user.self.port.input.AuthenticateGeneralUserQuery.AuthenticateGeneralUserQueryResult
import com.reservation.user.self.port.input.AuthenticateGeneralUserQuery.GeneralUserQueryDto
import com.reservation.user.self.port.output.AuthenticateGeneralUser
import com.reservation.user.self.port.output.UpdateAuthenticateResult
import com.reservation.user.self.port.output.UpdateAuthenticateResult.UpdateAuthenticateResultDto
import com.reservation.utilities.provider.JWTRecord
import com.reservation.utilities.provider.TokenProvider
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
                    throw NoSuchDatabaseElementException()
                }

        val authenticated = authenticateSignInService.signIn(authenticate, request.password)

        // histories 저장
        createAccessHistory(authenticated.accessHistories())

        updateAuthenticateResult(authenticated)

        return tokenize(authenticated)
    }

    private fun createAccessHistory(accessHistories: List<AccessHistory>) {
        createUserHistoriesCommand.execute(
            accessHistories.map {
                CreateUserHistoryCommandDto(
                    it.authenticateId,
                    it.loginId,
                    it.accessStatus(),
                    it.accessDateTime(),
                )
            },
        )
    }

    private fun updateAuthenticateResult(authenticated: Authenticate) {
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
    }

    private fun tokenize(authenticated: Authenticate): AuthenticateGeneralUserQueryResult {
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
