package com.reservation.seller.self.usecase

import com.reservation.authenticate.AccessHistory
import com.reservation.authenticate.Authenticate
import com.reservation.authenticate.port.input.AuthenticateSellerUserQuery
import com.reservation.authenticate.port.input.AuthenticateSellerUserQuery.AuthenticateSellerUserQueryResult
import com.reservation.authenticate.port.input.AuthenticateSellerUserQuery.SellerUserQueryDto
import com.reservation.authenticate.port.output.AuthenticateSellerUser
import com.reservation.authenticate.port.output.SaveSellerUserRefreshToken
import com.reservation.authenticate.port.output.SaveSellerUserRefreshToken.SaveSellerUserRefreshTokenInquiry
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
class AuthenticateSellerUserUseCase(
    private val authenticateSignInService: AuthenticateSignInService,
    private val authenticateSellerUser: AuthenticateSellerUser,
    private val createUserAccessHistoriesCommand: CreateUserAccessHistoriesCommand,
    private val updateAuthenticateResult: UpdateAuthenticateResult,
    private val tokenProvider: TokenProvider<JWTRecord>,
    private val saveSellerUserRefreshToken: SaveSellerUserRefreshToken,
) : AuthenticateSellerUserQuery {
    @Transactional
    override fun execute(request: SellerUserQueryDto): AuthenticateSellerUserQueryResult {
        val authenticate =
            authenticateSellerUser.query(request.toInquiry())
                ?.toDomain()
                ?: throw NoSuchPersistedElementException()

        val authenticated = authenticateSignInService.signIn(authenticate, request.password)

        createAccessHistory(authenticated.accessHistories)
        updateAuthenticateResult(authenticated)

        checkUserWasLockedDown(authenticated)
        checkUserPasswordMatchWasFailed(authenticated)

        val result = tokenize(authenticated)
        saveSellerUserRefreshToken.command(
            SaveSellerUserRefreshTokenInquiry(
                authenticated.id,
                result.refreshToken,
                result.refreshTokenExpiresIn,
            ),
        )
        return result
    }

    private fun createAccessHistory(accessHistories: List<AccessHistory>) {
        createUserAccessHistoriesCommand.execute(
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
        updateAuthenticateResult.command(
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

    private fun tokenize(authenticated: Authenticate): AuthenticateSellerUserQueryResult {
        val record =
            JWTRecord(
                authenticated.id,
                authenticated.loginId(),
                authenticated.role,
            )

        val refreshDuration = tokenProvider.duration
        val accessToken = tokenProvider.tokenize(record)
        val refreshToken = tokenProvider.tokenize(record, JWTType.REFRESH_TOKEN)
        return AuthenticateSellerUserQueryResult(
            accessToken,
            refreshToken,
            refreshDuration,
        )
    }
}
