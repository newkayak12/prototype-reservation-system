package com.reservation.persistence.restaurant.repository.dsl

import com.querydsl.core.types.dsl.BooleanExpression
import com.reservation.persistence.restaurant.QRestaurantPhotoEntity.restaurantPhotoEntity

object RestaurantPhotoQuerySpec {
    fun photoRestaurantIdentifiersIn(identifiers: Set<String>): BooleanExpression =
        restaurantPhotoEntity.restaurant.identifier.`in`(identifiers)
}
