package com.reservation.resign.config

import com.reservation.properties.BidirectionalEncryptProperties
import com.reservation.user.resign.service.ResignUserService
import com.reservation.utilities.encrypt.bidirectional.BidirectionalEncryptUtility
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ResignServiceFactory {
    @Bean
    fun resignUserService(property: BidirectionalEncryptProperties): ResignUserService =
        ResignUserService(BidirectionalEncryptUtility(property.secret))
}
