package com.reservation.rest.category.cuisine

import com.reservation.category.cuisine.port.input.FindCuisinesQuery
import com.reservation.rest.category.CategoryUrl
import com.reservation.rest.category.request.FindCuisineRequest
import com.reservation.rest.category.response.FindCuisineResponse
import com.reservation.rest.common.response.ListResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class FindCuisineController(
    private val findCuisinesQuery: FindCuisinesQuery,
) {
    @GetMapping(CategoryUrl.CUISINE)
    fun findNationalities(request: FindCuisineRequest): ListResponse<FindCuisineResponse> =
        ListResponse.ok(
            findCuisinesQuery.execute(request.toQuery())
                .map {
                    FindCuisineResponse(
                        it.id,
                        it.title,
                        it.categoryType,
                    )
                },
        )
}
