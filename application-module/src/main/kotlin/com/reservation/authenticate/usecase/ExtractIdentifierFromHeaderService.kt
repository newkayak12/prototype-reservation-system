package com.reservation.authenticate.usecase

import com.reservation.authenticate.port.input.ExtractIdentifierFromHeaderUseCase
import com.reservation.config.annotations.UseCase
import com.reservation.enumeration.JWTType.ACCESS_TOKEN
import com.reservation.exceptions.UnauthorizedException
import com.reservation.utilities.provider.JWTProvider
import com.reservation.utilities.provider.JWTRecord

@UseCase
class ExtractIdentifierFromHeaderService(
    private val jwtProvider: JWTProvider,
) : ExtractIdentifierFromHeaderUseCase {
    override fun execute(bearerToken: String?): String {
        if (bearerToken == null) {
            throw UnauthorizedException()
        }

        val record: JWTRecord = jwtProvider.decrypt(bearerToken, ACCESS_TOKEN)
        return record.id
    }
}
