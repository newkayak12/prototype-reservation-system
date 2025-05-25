package com.reservation.jwt.provider

interface Tokenable {
    fun identity(): String

    fun role(): String

    fun username(): String
}
