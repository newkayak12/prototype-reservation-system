package com.reservation.persistence.menu.repository.dsl

import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.Expressions
import com.reservation.persistence.menu.entity.QMenuPhotoEntity.menuPhotoEntity

object MenuPhotoQuerySpec {
    fun restaurantIdsIn(menuIdentifiers: Set<String>?): BooleanExpression? =
        menuIdentifiers?.let {
            if (it.isEmpty()) return@let Expressions.FALSE

            return@let menuPhotoEntity.menu.identifier.`in`(it)
        }
}
