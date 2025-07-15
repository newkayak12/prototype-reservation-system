package com.reservation.persistence.restaurant.repository.dsl

import com.querydsl.core.types.dsl.BooleanExpression
import com.reservation.persistence.restaurant.QRestaurantEntity.restaurantEntity

object RestaurantQuerySpec {
    fun companyIdEq(companyId: String?): BooleanExpression? =
        companyId?.let { restaurantEntity.companyId.eq(it) }

    fun nameEq(name: String?): BooleanExpression? = name?.let { restaurantEntity.name.eq(name) }
}
