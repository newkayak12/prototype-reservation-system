package com.reservation.category.tag.port.input

import com.reservation.category.tag.port.input.query.request.FindTagsQuery
import com.reservation.category.tag.port.input.query.response.FindTagsQueryResult

interface FindTagsUseCase {
    fun execute(request: FindTagsQuery): List<FindTagsQueryResult>
}
