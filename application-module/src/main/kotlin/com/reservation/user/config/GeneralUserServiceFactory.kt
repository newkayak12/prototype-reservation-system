package com.reservation.user.config

import com.reservation.user.self.service.CreateGeneralUserDomainService
import com.reservation.user.service.ChangeGeneralUserPasswordDomainService
import com.reservation.user.service.ChangeUserNicknameDomainService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GeneralUserServiceFactory {
    @Bean
    fun createGeneralUserDomainService(): CreateGeneralUserDomainService =
        CreateGeneralUserDomainService()

    @Bean
    fun changeGeneralUserPasswordDomainService(): ChangeGeneralUserPasswordDomainService =
        ChangeGeneralUserPasswordDomainService()

    @Bean
    fun changeUserNicknameDomainService(): ChangeUserNicknameDomainService =
        ChangeUserNicknameDomainService()
}
