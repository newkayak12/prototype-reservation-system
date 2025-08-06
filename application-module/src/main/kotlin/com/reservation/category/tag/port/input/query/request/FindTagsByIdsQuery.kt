package com.reservation.category.tag.port.input.query.request

import com.reservation.category.tag.port.output.FindTags.FindTagsInquiry

data class FindTagsByIdsQuery(
    val ids: List<Long>?,
) {
    fun toInquiry() = FindTagsInquiry(ids, null)
}
