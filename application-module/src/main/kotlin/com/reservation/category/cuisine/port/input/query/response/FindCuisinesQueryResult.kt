package com.reservation.category.cuisine.port.input.query.response

import com.reservation.enumeration.CategoryType

data class FindCuisinesQueryResult(
    val id: Long,
    val title: String,
    val categoryType: CategoryType,
)
