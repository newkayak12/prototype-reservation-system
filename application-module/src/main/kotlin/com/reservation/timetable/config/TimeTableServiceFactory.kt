package com.reservation.timetable.config

import com.reservation.timetable.service.CreateTimeTableOccupancyDomainService
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component

@Component
class TimeTableServiceFactory {
    @Bean
    fun createTimeTableOccupancyDomainService() = CreateTimeTableOccupancyDomainService()
}
