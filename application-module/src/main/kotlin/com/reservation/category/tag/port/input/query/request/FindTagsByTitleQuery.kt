package com.reservation.category.tag.port.input.query.request

import com.reservation.category.tag.port.output.FindTags.FindTagsInquiry

data class FindTagsByTitleQuery(
    val title: String?,
) {
    fun toInquiry() = FindTagsInquiry(null, title)
}
