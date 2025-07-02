package com.reservation.category.nationality.port.input

import com.reservation.category.nationality.port.output.FindNationalities.FindNationalitiesInquiry
import com.reservation.enumeration.CategoryType

fun interface FindNationalitiesQuery {
    fun execute(request: FindNationalitiesQueryDto): List<FindNationalitiesQueryResult>

    data class FindNationalitiesQueryDto(
        val title: String?,
    ) {
        fun toInquiry() = FindNationalitiesInquiry(title)
    }

    data class FindNationalitiesQueryResult(
        val id: Long,
        val title: String,
        val categoryType: CategoryType,
    )
}
