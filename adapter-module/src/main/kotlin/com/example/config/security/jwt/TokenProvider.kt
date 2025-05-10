package com.example.config.security.jwt

interface TokenProvider<E: Tokenable> {

    fun tokenize(tokenable: E): String

    fun validate(token: String): Boolean

    fun decrypt(token: String): E
}