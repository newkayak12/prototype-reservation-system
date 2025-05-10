package com.example.encoder.password

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

object PasswordEncoderUtility {
    private val passwordEncoder = BCryptPasswordEncoder();

    fun getInstance() = passwordEncoder
}