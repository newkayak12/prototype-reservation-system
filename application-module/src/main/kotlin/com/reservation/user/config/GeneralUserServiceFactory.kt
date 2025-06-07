package com.reservation.user.config

import com.reservation.user.self.service.CreateGeneralUserService
import com.reservation.user.service.ChangeGeneralUserPasswordService
import com.reservation.user.service.ChangeUserNicknameService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GeneralUserServiceFactory {
    @Bean
    fun createGeneralUserService(): CreateGeneralUserService = CreateGeneralUserService()

    @Bean
    fun changeGeneralUserPasswordService(): ChangeGeneralUserPasswordService =
        ChangeGeneralUserPasswordService()

    @Bean
    fun changeUserNicknameService(): ChangeUserNicknameService = ChangeUserNicknameService()
}
