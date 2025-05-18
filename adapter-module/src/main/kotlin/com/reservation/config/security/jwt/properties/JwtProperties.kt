package com.reservation.config.security.jwt.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(value = "security.jwt.properties")
data class JwtProperties(
    val expireTime: Long,
    val secret: String,
    val issuer: String,
)
