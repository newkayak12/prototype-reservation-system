package com.reservation.authenticate.usecase

import com.reservation.authenticate.AccessHistory
import com.reservation.authenticate.Authenticate
import com.reservation.authenticate.port.input.AuthenticateGeneralUserUseCase
import com.reservation.authenticate.port.input.query.request.GeneralUserQuery
import com.reservation.authenticate.port.input.query.response.AuthenticateGeneralUserQueryResult
import com.reservation.authenticate.port.output.AuthenticateGeneralUser
import com.reservation.authenticate.port.output.SaveGeneralUserRefreshToken
import com.reservation.authenticate.port.output.SaveGeneralUserRefreshToken.SaveRefreshTokenInquiry
import com.reservation.authenticate.port.output.UpdateAuthenticateResult
import com.reservation.authenticate.port.output.UpdateAuthenticateResult.UpdateAuthenticateResultDto
import com.reservation.authenticate.service.AuthenticateSignInDomainService
import com.reservation.common.exceptions.AccessFailureCountHasExceedException
import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.common.exceptions.WrongLoginIdOrPasswordException
import com.reservation.config.annotations.UseCase
import com.reservation.enumeration.JWTType
import com.reservation.user.history.access.port.input.CreateUserAccessHistoriesUseCase
import com.reservation.user.history.access.port.input.command.request.CreateUserHistoryCommand
import com.reservation.utilities.provider.JWTRecord
import com.reservation.utilities.provider.TokenProvider
import org.springframework.transaction.annotation.Transactional

@UseCase
class AuthenticateGeneralUserService(
    private val authenticateSignInDomainService: AuthenticateSignInDomainService,
    private val authenticateGeneralUser: AuthenticateGeneralUser,
    private val createUserHistoriesCommand: CreateUserAccessHistoriesUseCase,
    private val updateGeneralUserAuthenticateResult: UpdateAuthenticateResult,
    private val tokenProvider: TokenProvider<JWTRecord>,
    private val saveGeneralUserRefreshToken: SaveGeneralUserRefreshToken,
) : AuthenticateGeneralUserUseCase {
    @Transactional(
        noRollbackFor = [
            WrongLoginIdOrPasswordException::class, AccessFailureCountHasExceedException::class,
        ],
    )
    override fun execute(request: GeneralUserQuery): AuthenticateGeneralUserQueryResult {
        val authenticate =
            authenticateGeneralUser.query(request.toInquiry())?.toDomain()
                ?: run {
                    throw NoSuchPersistedElementException()
                }

        val authenticated = authenticateSignInDomainService.signIn(authenticate, request.password)

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
                CreateUserHistoryCommand(
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
