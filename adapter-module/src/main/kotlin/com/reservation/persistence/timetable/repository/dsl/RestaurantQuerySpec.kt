package com.reservation.persistence.timetable.repository.dsl

import com.querydsl.core.types.dsl.BooleanExpression
import com.reservation.persistence.timetable.entity.QTimeTableEntity.timeTableEntity

object RestaurantQuerySpec {
    fun restaurantIdEq(restaurantId: String): BooleanExpression
    = timeTableEntity.restaurantId.eq(restaurantId)
}
