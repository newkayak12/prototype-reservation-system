package com.reservation.persistence.schedule.repository.jpa

import com.reservation.persistence.schedule.entity.HolidayEntity
import org.springframework.data.repository.CrudRepository

interface HolidayJpaRepository : CrudRepository<HolidayEntity, String> {
    fun findAllByRestaurantId(restaurantId: String): List<HolidayEntity>
}
