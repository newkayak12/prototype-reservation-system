package com.reservation.user.config

import com.reservation.authenticate.service.AuthenticateSignInService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AuthenticateServiceFactory {
    @Bean
    fun authenticateSignInService(): AuthenticateSignInService = AuthenticateSignInService()
}
