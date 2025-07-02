package com.reservation.category.cuisine.port.output

import com.reservation.category.cuisine.port.input.FindCuisinesQuery.FindCuisinesQueryResult
import com.reservation.enumeration.CategoryType
import com.reservation.enumeration.CategoryType.NATIONALITY

fun interface FindCuisines {
    fun query(inquiry: FindCuisinesInquiry): List<FindCuisinesResult>

    data class FindCuisinesInquiry(
        val title: String?,
        val categoryType: CategoryType = NATIONALITY,
    )

    data class FindCuisinesResult(
        val id: Long,
        val title: String,
        val categoryType: CategoryType,
    ) {
        fun toQuery() = FindCuisinesQueryResult(id, title, categoryType)
    }
}
