package com.reservation.category.tag.port.input

import com.reservation.category.tag.port.output.FindTags.FindTagsInquiry
import com.reservation.enumeration.CategoryType

fun interface FindTagsQuery {
    fun execute(request: FindTagsQueryDto): List<FindTagsQueryResult>

    data class FindTagsQueryDto(
        val title: String?,
    ) {
        fun toInquiry() = FindTagsInquiry(title)
    }

    data class FindTagsQueryResult(
        val id: Long,
        val title: String,
        val categoryType: CategoryType,
    )
}
