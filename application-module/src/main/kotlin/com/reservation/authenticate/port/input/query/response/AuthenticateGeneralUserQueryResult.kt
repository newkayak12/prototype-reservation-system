package com.reservation.authenticate.port.input.query.response

data class AuthenticateGeneralUserQueryResult(
    val accessToken: String,
    val refreshToken: String,
    val refreshTokenExpiresIn: Long,
)
