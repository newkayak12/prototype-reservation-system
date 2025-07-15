package com.reservation.authenticate.usecase

import com.reservation.authenticate.port.input.ExtractIdentifierFromHeaderQuery
import com.reservation.config.annotations.UseCase
import com.reservation.enumeration.JWTType.ACCESS_TOKEN
import com.reservation.exceptions.UnauthorizedException
import com.reservation.utilities.provider.JWTProvider
import com.reservation.utilities.provider.JWTRecord

@UseCase
class ExtractIdentifierFromHeaderUseCase(
    private val jwtProvider: JWTProvider,
) : ExtractIdentifierFromHeaderQuery {
    override fun execute(bearerToken: String?): String {
        if (bearerToken == null) {
            throw UnauthorizedException()
        }

        val record: JWTRecord = jwtProvider.decrypt(bearerToken, ACCESS_TOKEN)
        return record.id
    }
}
