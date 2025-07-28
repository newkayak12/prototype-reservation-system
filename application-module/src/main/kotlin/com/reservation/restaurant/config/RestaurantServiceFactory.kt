package com.reservation.restaurant.config

import com.reservation.restaurant.service.ChangeRestaurantDomainService
import com.reservation.restaurant.service.CreateRestaurantDomainService
import com.reservation.restaurant.service.update.UpdateCuisines
import com.reservation.restaurant.service.update.UpdateNationalities
import com.reservation.restaurant.service.update.UpdatePhoto
import com.reservation.restaurant.service.update.UpdateRoutine
import com.reservation.restaurant.service.update.UpdateTag
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RestaurantServiceFactory {
    @Bean
    fun createRestaurantDomainService() = CreateRestaurantDomainService()

    @Bean
    fun changeRestaurantDomainService() =
        ChangeRestaurantDomainService(
            UpdateRoutine(),
            UpdatePhoto(),
            UpdateTag(),
            UpdateNationalities(),
            UpdateCuisines(),
        )
}
