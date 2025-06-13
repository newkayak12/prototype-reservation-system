package com.reservation.config

import com.reservation.config.security.jwt.properties.JWTProperties
import com.reservation.config.security.jwt.properties.JwtWhitelist
import com.reservation.config.security.xss.properties.XssBlacklist
import com.reservation.properties.BidirectionalEncryptProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(
    value = [
        JWTProperties::class, JwtWhitelist::class,
        XssBlacklist::class, BidirectionalEncryptProperties::class,
    ],
)
class PropertiesConfig
