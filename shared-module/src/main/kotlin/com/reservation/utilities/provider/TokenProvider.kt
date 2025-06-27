package com.reservation.utilities.provider

import com.reservation.enumeration.JWTType

interface TokenProvider<E : Tokenable> {
    fun tokenize(
        tokenable: E,
        type: JWTType = JWTType.ACCESS_TOKEN,
    ): String

    fun validate(
        token: String,
        type: JWTType = JWTType.ACCESS_TOKEN,
    ): Boolean

    fun decrypt(
        token: String,
        type: JWTType = JWTType.ACCESS_TOKEN,
    ): E

    fun duration(): Long
}
