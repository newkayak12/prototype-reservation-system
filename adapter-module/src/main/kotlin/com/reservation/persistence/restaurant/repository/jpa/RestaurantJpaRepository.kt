package com.reservation.persistence.restaurant.repository.jpa

import com.reservation.persistence.restaurant.RestaurantEntity
import org.springframework.data.repository.CrudRepository

interface RestaurantJpaRepository : CrudRepository<RestaurantEntity, String>
