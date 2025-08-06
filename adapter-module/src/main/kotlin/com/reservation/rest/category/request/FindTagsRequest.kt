package com.reservation.rest.category.request

import com.reservation.category.tag.port.input.query.request.FindTagsByTitleQuery

data class FindTagsRequest(val title: String?) {
    fun toQuery() = FindTagsByTitleQuery(title)
}
