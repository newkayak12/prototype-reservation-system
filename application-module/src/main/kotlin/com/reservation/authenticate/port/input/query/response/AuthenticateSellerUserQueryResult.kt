package com.reservation.authenticate.port.input.query.response

data class AuthenticateSellerUserQueryResult(
    val accessToken: String,
    val refreshToken: String,
    val refreshTokenExpiresIn: Long,
)
