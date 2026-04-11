package com.reservation.persistence.restaurant.repository.jpa

import com.reservation.persistence.restaurant.entity.RestaurantEntity
import org.springframework.data.repository.CrudRepository
import java.util.Optional

interface RestaurantJpaRepository : CrudRepository<RestaurantEntity, String> {
    fun findRestaurantEntityById(id: String): Optional<RestaurantEntity>
}
