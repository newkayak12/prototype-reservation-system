package com.reservation.authenticate.usecase

import com.reservation.authenticate.AccessHistory
import com.reservation.authenticate.Authenticate
import com.reservation.authenticate.port.input.AuthenticateSellerUserUseCase
import com.reservation.authenticate.port.input.query.request.SellerUserQuery
import com.reservation.authenticate.port.input.query.response.AuthenticateSellerUserQueryResult
import com.reservation.authenticate.port.output.AuthenticateSellerUser
import com.reservation.authenticate.port.output.SaveSellerUserRefreshToken
import com.reservation.authenticate.port.output.SaveSellerUserRefreshToken.SaveSellerUserRefreshTokenInquiry
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
class AuthenticateSellerUserService(
    private val authenticateSignInDomainService: AuthenticateSignInDomainService,
    private val authenticateSellerUser: AuthenticateSellerUser,
    private val createUserAccessHistoriesUseCase: CreateUserAccessHistoriesUseCase,
    private val updateAuthenticateResult: UpdateAuthenticateResult,
    private val tokenProvider: TokenProvider<JWTRecord>,
    private val saveSellerUserRefreshToken: SaveSellerUserRefreshToken,
) : AuthenticateSellerUserUseCase {
    @Transactional
    override fun execute(request: SellerUserQuery): AuthenticateSellerUserQueryResult {
        val authenticate =
            authenticateSellerUser.query(request.toInquiry())
                ?.toDomain()
                ?: throw NoSuchPersistedElementException()

        val authenticated = authenticateSignInDomainService.signIn(authenticate, request.password)

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
        createUserAccessHistoriesUseCase.execute(
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
