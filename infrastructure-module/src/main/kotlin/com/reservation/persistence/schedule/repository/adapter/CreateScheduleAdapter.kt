package com.reservation.persistence.schedule.repository.adapter

import com.reservation.persistence.schedule.entity.ScheduleEntity
import com.reservation.persistence.schedule.repository.jpa.ScheduleJpaRepository
import com.reservation.schedule.port.output.CreateSchedule
import com.reservation.schedule.port.output.CreateSchedule.CreateScheduleInquiry
import org.springframework.stereotype.Component

@Component
class CreateScheduleAdapter(
    private val jpaRepository: ScheduleJpaRepository,
) : CreateSchedule {
    override fun command(command: CreateScheduleInquiry): Boolean {
        val schedule = ScheduleEntity(restaurantId = command.restaurantId)

        return jpaRepository.save(schedule) != null
    }
}
