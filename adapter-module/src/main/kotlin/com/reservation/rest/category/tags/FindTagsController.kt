package com.reservation.rest.category.tags

import com.reservation.category.tag.port.input.FindTagsQuery
import com.reservation.rest.category.CategoryUrl
import com.reservation.rest.category.request.FindTagsRequest
import com.reservation.rest.category.response.FindTagsResponse
import com.reservation.rest.common.response.ListResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class FindTagsController(
    val findTagsQuery: FindTagsQuery,
) {
    @GetMapping(CategoryUrl.TAGS)
    fun findTags(request: FindTagsRequest): ListResponse<FindTagsResponse> =
        ListResponse.ok(
            findTagsQuery.execute(request.toQuery())
                .map {
                    FindTagsResponse(
                        it.id,
                        it.title,
                        it.categoryType,
                    )
                },
        )
}
