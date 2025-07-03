package com.reservation.rest.category.response

import com.reservation.enumeration.CategoryType

data class FindTagsResponse(
    val id: Long,
    val title: String,
    val categoryType: CategoryType,
)
