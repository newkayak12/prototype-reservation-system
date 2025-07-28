package com.reservation.category.nationality.port.input.query.response

import com.reservation.enumeration.CategoryType

data class FindNationalitiesQueryResult(
    val id: Long,
    val title: String,
    val categoryType: CategoryType,
)
