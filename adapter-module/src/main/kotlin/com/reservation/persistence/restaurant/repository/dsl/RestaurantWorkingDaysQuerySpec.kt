package com.reservation.persistence.restaurant.repository.dsl

import com.querydsl.core.types.dsl.BooleanExpression
import com.reservation.persistence.restaurant.QRestaurantWorkingDayEntity.restaurantWorkingDayEntity

object RestaurantWorkingDaysQuerySpec {
    fun workingDayRestaurantIdentifiersIn(identifiers: Set<String>): BooleanExpression =
        restaurantWorkingDayEntity.restaurant.identifier.`in`(identifiers)
}
