package com.reservation.persistence.schedule.repository.jpa

import com.reservation.persistence.schedule.entity.TimeSpanEntity
import org.springframework.data.repository.CrudRepository

interface TimeSpanJpaRepository : CrudRepository<TimeSpanEntity, String> {
    fun findAllByRestaurantId(restaurantId: String): List<TimeSpanEntity>
}
