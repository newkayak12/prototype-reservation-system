package com.reservation.persistence.restaurant.repository.dsl

import com.querydsl.core.types.dsl.BooleanExpression
import com.reservation.persistence.restaurant.QRestaurantEntity.restaurantEntity
import org.springframework.util.StringUtils

object RestaurantQuerySpec {
    fun identifierEq(identifier: String): BooleanExpression =
        restaurantEntity.identifier.eq(identifier)

    fun identifierAfter(identifier: String): BooleanExpression? =
        if (StringUtils.hasText(identifier)) {
            restaurantEntity.identifier.gt(identifier)
        } else {
            null
        }

    fun isNotDeleted(): BooleanExpression = restaurantEntity.logicalDelete.isDeleted.isFalse

    fun companyIdEq(companyId: String?): BooleanExpression? =
        companyId?.let { restaurantEntity.companyId.eq(it) }

    fun nameEq(name: String?): BooleanExpression? = name?.let { restaurantEntity.name.eq(name) }

    fun nameContains(name: String?): BooleanExpression? =
        name?.let { restaurantEntity.name.contains(name) }
}
