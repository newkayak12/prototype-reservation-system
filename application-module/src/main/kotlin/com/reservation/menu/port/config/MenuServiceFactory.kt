package com.reservation.menu.port.config

import com.reservation.menu.service.CreateMenuDomainService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MenuServiceFactory {
    @Bean
    fun createMenuDomainService(): CreateMenuDomainService = CreateMenuDomainService()
}
