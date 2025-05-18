package com.reservation.config.security.jwt

interface Tokenable {
    fun identity(): String

    fun role(): String

    fun getUsername(): String
}
