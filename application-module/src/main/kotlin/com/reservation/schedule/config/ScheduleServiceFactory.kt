package com.reservation.schedule.config

import com.reservation.schedule.service.CreateHolidayDomainService
import com.reservation.schedule.service.CreateScheduleDomainService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ScheduleServiceFactory {
    @Bean
    fun createScheduleDomainService() = CreateScheduleDomainService()

    @Bean
    fun createHolidayDomainService() = CreateHolidayDomainService()
}
