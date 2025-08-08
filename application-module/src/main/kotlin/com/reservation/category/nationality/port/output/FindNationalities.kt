package com.reservation.category.nationality.port.output

import com.reservation.category.nationality.port.input.query.response.FindNationalitiesQueryResult
import com.reservation.enumeration.CategoryType
import com.reservation.enumeration.CategoryType.NATIONALITY

interface FindNationalities {
    fun query(inquiry: FindNationalitiesInquiry): List<FindNationalitiesResult>

    data class FindNationalitiesInquiry(
        val ids: List<Long>?,
        val title: String?,
        val categoryType: CategoryType = NATIONALITY,
    )

    data class FindNationalitiesResult(
        val id: Long,
        val title: String,
        val categoryType: CategoryType,
    ) {
        fun toQuery() = FindNationalitiesQueryResult(id, title, categoryType)
    }
}
