package com.reservation.category.tag.port.input.query.request

import com.reservation.category.tag.port.output.FindTags.FindTagsInquiry

data class FindTagsQuery(
    val title: String?,
) {
    fun toInquiry() = FindTagsInquiry(title)
}
