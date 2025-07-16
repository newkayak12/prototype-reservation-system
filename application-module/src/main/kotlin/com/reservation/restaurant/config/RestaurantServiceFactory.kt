package com.reservation.restaurant.config

import com.reservation.restaurant.service.CreateRestaurantService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RestaurantServiceFactory {
    @Bean
    fun createRestaurantService() = CreateRestaurantService()
}
