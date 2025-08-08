package com.reservation.rest.category.cuisine

import com.reservation.category.cuisine.port.input.FindCuisinesByTitleUseCase
import com.reservation.rest.category.CategoryUrl
import com.reservation.rest.category.request.FindCuisineRequest
import com.reservation.rest.category.response.FindCuisineResponse
import com.reservation.rest.common.response.ListResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class FindCuisineController(
    private val findCuisinesByTitleUseCase: FindCuisinesByTitleUseCase,
) {
    @GetMapping(CategoryUrl.CUISINE)
    fun findCuisines(request: FindCuisineRequest): ListResponse<FindCuisineResponse> =
        ListResponse.ok(
            findCuisinesByTitleUseCase.execute(request.toQuery())
                .map {
                    FindCuisineResponse(
                        it.id,
                        it.title,
                        it.categoryType,
                    )
                },
        )
}
