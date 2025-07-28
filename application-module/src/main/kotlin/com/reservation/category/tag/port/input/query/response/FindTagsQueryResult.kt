package com.reservation.category.tag.port.input.query.response

import com.reservation.enumeration.CategoryType

data class FindTagsQueryResult(
    val id: Long,
    val title: String,
    val categoryType: CategoryType,
)
