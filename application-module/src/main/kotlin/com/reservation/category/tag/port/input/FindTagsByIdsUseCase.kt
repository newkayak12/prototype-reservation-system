package com.reservation.category.tag.port.input

import com.reservation.category.tag.port.input.query.request.FindTagsByIdsQuery
import com.reservation.category.tag.port.input.query.response.FindTagsQueryResult

interface FindTagsByIdsUseCase {
    fun execute(request: FindTagsByIdsQuery): List<FindTagsQueryResult>
}
