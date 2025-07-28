package com.reservation.authenticate.config

import com.reservation.authenticate.service.AuthenticateSignInDomainService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AuthenticateServiceFactory {
    @Bean
    fun authenticateSignInDomainService(): AuthenticateSignInDomainService =
        AuthenticateSignInDomainService()
}
