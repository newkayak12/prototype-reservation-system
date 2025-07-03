package com.reservation.category.tag.port.output

import com.reservation.category.tag.port.input.FindTagsQuery.FindTagsQueryResult
import com.reservation.enumeration.CategoryType
import com.reservation.enumeration.CategoryType.CUISINE

fun interface FindTags {
    fun query(inquiry: FindTagsInquiry): List<FindTagsResult>

    data class FindTagsInquiry(
        val title: String?,
        val categoryType: CategoryType = CUISINE,
    )

    data class FindTagsResult(
        val id: Long,
        val title: String,
        val categoryType: CategoryType,
    ) {
        fun toQuery() = FindTagsQueryResult(id, title, categoryType)
    }
}
