package com.reservation.user.self.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.enumeration.JWTType.ACCESS_TOKEN
import com.reservation.enumeration.JWTType.REFRESH_TOKEN
import com.reservation.exceptions.AlreadyExpiredException
import com.reservation.exceptions.InvalidTokenException
import com.reservation.exceptions.UnauthorizedException
import com.reservation.user.self.port.input.RefreshGeneralUserAccessTokenQuery
import com.reservation.user.self.port.input.RefreshGeneralUserAccessTokenQuery.RefreshResult
import com.reservation.user.self.port.output.FindGeneralUserRefreshToken
import com.reservation.user.self.port.output.FindGeneralUserRefreshToken.FindRefreshTokenInquiry
import com.reservation.user.self.port.output.SaveGeneralUserRefreshToken
import com.reservation.user.self.port.output.SaveGeneralUserRefreshToken.SaveRefreshTokenInquiry
import com.reservation.utilities.provider.JWTRecord
import com.reservation.utilities.provider.TokenProvider

@UseCase
class RefreshGeneralUserUseCase(
    private val tokenProvider: TokenProvider<JWTRecord>,
    private val findGeneralUserRefreshToken: FindGeneralUserRefreshToken,
    private val saveGeneralUserRefreshToken: SaveGeneralUserRefreshToken,
) : RefreshGeneralUserAccessTokenQuery {
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
            findGeneralUserRefreshToken.query(FindRefreshTokenInquiry(uuid))
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

        saveGeneralUserRefreshToken.command(
            SaveRefreshTokenInquiry(
                record.id,
                latestRefreshToken,
                tokenProvider.duration,
            ),
        )

        return RefreshResult(latestAccessToken, latestRefreshToken)
    }
}
