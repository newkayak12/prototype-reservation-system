package com.reservation.persistence.menu.repository.dsl

import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.Expressions
import com.reservation.persistence.menu.entity.QMenuEntity.menuEntity

object MenuQuerySpec {
    fun restaurantIdEq(restaurantId: String?): BooleanExpression =
        restaurantId?.let { menuEntity.restaurantId.eq(it) }
            ?: Expressions.FALSE
}
