package com.reservation.config.security.jwt

import com.reservation.config.security.jwt.properties.JwtProperties
import com.reservation.utilities.provider.JWTProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JWTProviderConfig {
    @Bean
    fun jwtProvider(jwtProperties: JwtProperties): JWTProvider =
        JWTProvider(
            jwtProperties.secret,
            jwtProperties.expireTime,
            jwtProperties.issuer,
            jwtProperties.version,
        )
}
