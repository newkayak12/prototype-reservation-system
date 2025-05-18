package com.reservation.config.security.jwt.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(value = "security.jwt.allowed")
data class JwtWhitelist(
    val path: List<String>?,
)
