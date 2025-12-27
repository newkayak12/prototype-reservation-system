package com.reservation.reservation.config

import com.reservation.reservation.service.CreateReservationDomainService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ReservationConfig {
    @Bean
    fun createReservationDomainService() = CreateReservationDomainService()
}
