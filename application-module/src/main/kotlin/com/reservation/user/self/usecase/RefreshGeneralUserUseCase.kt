package com.reservation.user.self.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.enumeration.JWTType.ACCESS_TOKEN
import com.reservation.enumeration.JWTType.REFRESH_TOKEN
import com.reservation.exceptions.AlreadyExpiredException
import com.reservation.exceptions.InvalidTokenException
import com.reservation.exceptions.UnauthorizedException
import com.reservation.user.self.port.input.RefreshAccessTokenQuery
import com.reservation.user.self.port.input.RefreshAccessTokenQuery.RefreshResult
import com.reservation.user.self.port.output.FindRefreshToken
import com.reservation.user.self.port.output.FindRefreshToken.FindRefreshTokenInquiry
import com.reservation.user.self.port.output.SaveRefreshToken
import com.reservation.user.self.port.output.SaveRefreshToken.SaveRefreshTokenInquiry
import com.reservation.utilities.provider.JWTRecord
import com.reservation.utilities.provider.TokenProvider

@UseCase
class RefreshGeneralUserUseCase(
    private val tokenProvider: TokenProvider<JWTRecord>,
    private val findRefreshToken: FindRefreshToken,
    private val saveRefreshToken: SaveRefreshToken,
) : RefreshAccessTokenQuery {
    private fun validateJWTs(refreshToken: String) {
        if (
            refreshToken.isBlank() ||
            !tokenProvider.validate(refreshToken, REFRESH_TOKEN)
        ) {
            throw UnauthorizedException()
        }
    }

    private fun validatePersisted(
        uuid: String,
        refreshToken: String,
    ) {
        val persistedToken =
            findRefreshToken.query(FindRefreshTokenInquiry(uuid))
                ?: throw AlreadyExpiredException()

        if (persistedToken != refreshToken) {
            throw InvalidTokenException()
        }
    }

    private fun decrypt(refreshToken: String): JWTRecord =
        tokenProvider.decrypt(refreshToken, REFRESH_TOKEN)

    override fun refresh(refreshToken: String): RefreshResult {
        validateJWTs(refreshToken)

        val record = decrypt(refreshToken)

        validatePersisted(record.id, refreshToken)

        val latestAccessToken = tokenProvider.tokenize(record, ACCESS_TOKEN)
        val latestRefreshToken = tokenProvider.tokenize(record, REFRESH_TOKEN)

        saveRefreshToken.command(
            SaveRefreshTokenInquiry(
                record.id,
                latestRefreshToken,
                tokenProvider.duration,
            ),
        )

        return RefreshResult(latestAccessToken, latestRefreshToken)
    }
}
