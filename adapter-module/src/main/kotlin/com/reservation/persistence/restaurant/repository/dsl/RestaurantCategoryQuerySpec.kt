package com.reservation.persistence.restaurant.repository.dsl

import com.querydsl.core.types.dsl.BooleanExpression
import com.reservation.persistence.restaurant.entity.QRestaurantCuisinesEntity.restaurantCuisinesEntity
import com.reservation.persistence.restaurant.entity.QRestaurantNationalitiesEntity.restaurantNationalitiesEntity
import com.reservation.persistence.restaurant.entity.QRestaurantTagsEntity.restaurantTagsEntity

object RestaurantCategoryQuerySpec {
    fun tagsIn(ids: Set<Long>): BooleanExpression? =
        if (ids.isNotEmpty()) restaurantTagsEntity.tagsId.`in`(ids) else null

    fun nationalitiesIn(ids: Set<Long>): BooleanExpression? =
        if (ids.isNotEmpty()) restaurantNationalitiesEntity.nationalitiesId.`in`(ids) else null

    fun cuisinesIn(ids: Set<Long>): BooleanExpression? =
        if (ids.isNotEmpty()) restaurantCuisinesEntity.cuisinesId.`in`(ids) else null
}
