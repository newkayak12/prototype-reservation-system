package com.reservation.category.shared

import com.reservation.enumeration.CategoryType

data class CategoryDetail(
    val title: String,
    val categoryType: CategoryType,
)
