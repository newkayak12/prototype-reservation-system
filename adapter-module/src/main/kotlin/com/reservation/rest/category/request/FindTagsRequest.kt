package com.reservation.rest.category.request

import com.reservation.category.tag.port.input.FindTagsQuery.FindTagsQueryDto

data class FindTagsRequest(val title: String?) {
    fun toQuery() = FindTagsQueryDto(title)
}
