package com.reservation.enumeration

enum class JWTType(
    val title: String,
) {
    REFRESH_TOKEN("refresh_token"),
    ACCESS_TOKEN("access_token"),
}
