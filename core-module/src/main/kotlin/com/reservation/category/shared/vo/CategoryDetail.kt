package com.reservation.category.shared.vo

import com.reservation.enumeration.CategoryType

data class CategoryDetail(
    val title: String,
    val categoryType: CategoryType,
)
