package com.reservation.utilities.encrypt.password

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

object PasswordEncoderUtility {
    private val passwordEncoder = BCryptPasswordEncoder()

    fun getInstance() = passwordEncoder

    fun matches(
        rawCharSequence: String,
        encodedCharSequence: String,
    ): Boolean = passwordEncoder.matches(rawCharSequence, encodedCharSequence)

    fun encode(rawCharSequence: String): String = passwordEncoder.encode(rawCharSequence)
}
