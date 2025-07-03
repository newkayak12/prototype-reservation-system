package com.reservation.category.cuisine.port.input

import com.reservation.category.cuisine.port.output.FindCuisines.FindCuisinesInquiry
import com.reservation.enumeration.CategoryType

fun interface FindCuisinesQuery {
    fun execute(request: FindCuisinesQueryDto): List<FindCuisinesQueryResult>

    data class FindCuisinesQueryDto(
        val title: String?,
    ) {
        fun toInquiry() = FindCuisinesInquiry(title)
    }

    data class FindCuisinesQueryResult(
        val id: Long,
        val title: String,
        val categoryType: CategoryType,
    )
}
