package com.reservation.category.tag.port.input

import com.reservation.category.tag.port.input.query.request.FindTagsByTitleQuery
import com.reservation.category.tag.port.input.query.response.FindTagsQueryResult

interface FindTagsByTitleUseCase {
    fun execute(request: FindTagsByTitleQuery): List<FindTagsQueryResult>
}
