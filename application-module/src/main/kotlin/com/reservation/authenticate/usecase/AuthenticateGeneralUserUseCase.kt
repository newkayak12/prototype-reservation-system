package com.reservation.authenticate.usecase

import com.reservation.authenticate.AccessHistory
import com.reservation.authenticate.Authenticate
import com.reservation.authenticate.port.input.AuthenticateGeneralUserQuery
import com.reservation.authenticate.port.input.AuthenticateGeneralUserQuery.AuthenticateGeneralUserQueryResult
import com.reservation.authenticate.port.input.AuthenticateGeneralUserQuery.GeneralUserQueryDto
import com.reservation.authenticate.port.output.AuthenticateGeneralUser
import com.reservation.authenticate.port.output.SaveGeneralUserRefreshToken
import com.reservation.authenticate.port.output.SaveGeneralUserRefreshToken.SaveRefreshTokenInquiry
import com.reservation.authenticate.port.output.UpdateAuthenticateResult
import com.reservation.authenticate.port.output.UpdateAuthenticateResult.UpdateAuthenticateResultDto
import com.reservation.authenticate.service.AuthenticateSignInService
import com.reservation.common.exceptions.AccessFailureCountHasExceedException
import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.common.exceptions.WrongLoginIdOrPasswordException
import com.reservation.config.annotations.UseCase
import com.reservation.enumeration.JWTType
import com.reservation.user.history.access.port.input.CreateUserAccessHistoriesCommand
import com.reservation.user.history.access.port.input.CreateUserAccessHistoriesCommand.CreateUserHistoryCommandDto
import com.reservation.utilities.provider.JWTRecord
import com.reservation.utilities.provider.TokenProvider
import org.springframework.transaction.annotation.Transactional

@UseCase
class AuthenticateGeneralUserUseCase(
    private val authenticateSignInService: AuthenticateSignInService,
    private val authenticateGeneralUser: AuthenticateGeneralUser,
    private val createUserHistoriesCommand: CreateUserAccessHistoriesCommand,
    private val updateGeneralUserAuthenticateResult: UpdateAuthenticateResult,
    private val tokenProvider: TokenProvider<JWTRecord>,
    private val saveGeneralUserRefreshToken: SaveGeneralUserRefreshToken,
) : AuthenticateGeneralUserQuery {
    @Transactional(
        noRollbackFor = [
            WrongLoginIdOrPasswordException::class, AccessFailureCountHasExceedException::class,
        ],
    )
    override fun execute(request: GeneralUserQueryDto): AuthenticateGeneralUserQueryResult {
        val authenticate =
            authenticateGeneralUser.query(request.toInquiry())?.toDomain()
                ?: run {
                    throw NoSuchPersistedElementException()
                }

        val authenticated = authenticateSignInService.signIn(authenticate, request.password)

        // histories 저장
        createAccessHistory(authenticated.accessHistories)
        updateAuthenticateResult(authenticated)

        checkUserWasLockedDown(authenticated)
        checkUserPasswordMatchWasFailed(authenticated)
        val result = tokenize(authenticated)

        saveGeneralUserRefreshToken.command(
            SaveRefreshTokenInquiry(
                authenticated.id,
                result.refreshToken,
                result.refreshTokenExpiresIn,
            ),
        )
        return result
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
        // authenticated.
        updateGeneralUserAuthenticateResult.command(
            UpdateAuthenticateResultDto(
                authenticated.id,
                authenticated.failCount,
                authenticated.lockedDateTime,
                authenticated.userStatus,
            ),
        )
    }

    private fun checkUserWasLockedDown(authenticated: Authenticate) {
        if (!authenticated.lockCheckSuccess) {
            throw AccessFailureCountHasExceedException()
        }
    }

    private fun checkUserPasswordMatchWasFailed(authenticated: Authenticate) {
        if (!authenticated.passwordCheckSuccess) {
            throw WrongLoginIdOrPasswordException()
        }
    }

    private fun tokenize(authenticated: Authenticate): AuthenticateGeneralUserQueryResult {
        val record =
            JWTRecord(
                authenticated.id,
                authenticated.loginId(),
                authenticated.role,
            )

        val refreshDuration = tokenProvider.duration
        val accessToken = tokenProvider.tokenize(record)
        val refreshToken = tokenProvider.tokenize(record, JWTType.REFRESH_TOKEN)
        return AuthenticateGeneralUserQueryResult(
            accessToken,
            refreshToken,
            refreshDuration,
        )
    }
}
