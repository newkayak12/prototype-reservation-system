package com.reservation.persistence.schedule.repository.jpa

import com.reservation.persistence.schedule.entity.ScheduleEntity
import org.springframework.data.repository.CrudRepository

interface ScheduleJpaRepository : CrudRepository<ScheduleEntity, String> {
    fun findByRestaurantId(restaurantId: String): ScheduleEntity?
}
