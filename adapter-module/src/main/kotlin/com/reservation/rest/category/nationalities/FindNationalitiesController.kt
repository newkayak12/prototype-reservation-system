package com.reservation.rest.category.nationalities

import com.reservation.category.nationality.port.input.FindNationalitiesQuery
import com.reservation.rest.category.CategoryUrl
import com.reservation.rest.category.request.FindNationalitiesRequest
import com.reservation.rest.category.response.FindNationalitiesResponse
import com.reservation.rest.common.response.ListResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class FindNationalitiesController(
    private val findNationalitiesQuery: FindNationalitiesQuery,
) {
    @GetMapping(CategoryUrl.NATIONALITIES)
    fun findNationalities(
        request: FindNationalitiesRequest,
    ): ListResponse<FindNationalitiesResponse> =
        ListResponse.ok(
            findNationalitiesQuery.execute(request.toQuery())
                .map {
                    FindNationalitiesResponse(
                        it.id,
                        it.title,
                        it.categoryType,
                    )
                },
        )
}
