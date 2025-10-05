package com.reservation.persistence.schedule.repository.jpa

import com.reservation.persistence.schedule.entity.TableEntity
import org.springframework.data.repository.CrudRepository

interface TableJpaRepository: CrudRepository<TableEntity, String> {

    fun findAllByRestaurantId(restaurantId: String): List<TableEntity>
}
