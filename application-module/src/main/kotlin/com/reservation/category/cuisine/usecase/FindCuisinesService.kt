package com.reservation.category.cuisine.usecase

import com.reservation.category.cuisine.port.input.FindCuisinesByIdsUseCase
import com.reservation.category.cuisine.port.input.FindCuisinesByTitleUseCase
import com.reservation.category.cuisine.port.input.query.request.FindCuisinesByIdsQuery
import com.reservation.category.cuisine.port.input.query.request.FindCuisinesByTitleQuery
import com.reservation.category.cuisine.port.input.query.response.FindCuisinesQueryResult
import com.reservation.category.cuisine.port.output.FindCuisines
import com.reservation.config.annotations.UseCase
import org.springframework.transaction.annotation.Transactional

@UseCase
class FindCuisinesService(
    private val findCuisines: FindCuisines,
) : FindCuisinesByTitleUseCase, FindCuisinesByIdsUseCase {
    @Transactional(readOnly = true)
    override fun execute(request: FindCuisinesByTitleQuery): List<FindCuisinesQueryResult> {
        return findCuisines.query(request.toInquiry()).map { it.toQuery() }
    }

    @Transactional(readOnly = true)
    override fun execute(request: FindCuisinesByIdsQuery): List<FindCuisinesQueryResult> {
        return findCuisines.query(request.toInquiry()).map { it.toQuery() }
    }
}
