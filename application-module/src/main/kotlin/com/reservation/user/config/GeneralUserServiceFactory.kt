package com.reservation.user.config

import com.reservation.user.self.service.CreateGeneralUserService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GeneralUserServiceFactory {
    @Bean
    fun createGeneralUserService(): CreateGeneralUserService = CreateGeneralUserService()
}
