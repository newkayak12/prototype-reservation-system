package com.reservation.config.security.jwt

import com.reservation.config.security.jwt.properties.JWTProperties
import com.reservation.utilities.provider.JWTProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JWTProviderConfig {
    @Bean
    fun jwtProvider(jwtProperties: JWTProperties): JWTProvider =
        JWTProvider(
            secret = jwtProperties.secret,
            issuer = jwtProperties.issuer,
            version = jwtProperties.version,
            duration = jwtProperties.expireTime,
        )
}
