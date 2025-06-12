package com.reservation.user.self.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.enumeration.JWTType.ACCESS_TOKEN
import com.reservation.enumeration.JWTType.REFRESH_TOKEN
import com.reservation.exceptions.UnAuthorizedException
import com.reservation.user.self.port.input.RefreshAccessTokenQuery
import com.reservation.utilities.provider.JWTRecord
import com.reservation.utilities.provider.TokenProvider

@UseCase
class RefreshGeneralUserUseCase(
    private val tokenProvider: TokenProvider<JWTRecord>,
) : RefreshAccessTokenQuery {
    private fun validate(refreshToken: String) {
        if (
            refreshToken.isBlank() ||
            !tokenProvider.validate(refreshToken, REFRESH_TOKEN)
        ) {
            throw UnAuthorizedException()
        }
    }

    private fun decrypt(refreshToken: String): JWTRecord =
        tokenProvider.decrypt(refreshToken, REFRESH_TOKEN)

    override fun refresh(refreshToken: String): String {
        validate(refreshToken)
        val record = decrypt(refreshToken)
        return tokenProvider.tokenize(record, ACCESS_TOKEN)
    }
}
