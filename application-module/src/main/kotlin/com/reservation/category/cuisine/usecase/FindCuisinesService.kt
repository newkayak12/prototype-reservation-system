package com.reservation.category.cuisine.usecase

import com.reservation.category.cuisine.port.input.FindCuisinesUseCase
import com.reservation.category.cuisine.port.input.query.request.FindCuisinesQuery
import com.reservation.category.cuisine.port.input.query.response.FindCuisinesQueryResult
import com.reservation.category.cuisine.port.output.FindCuisines
import com.reservation.config.annotations.UseCase
import org.springframework.transaction.annotation.Transactional

@UseCase
class FindCuisinesService(
    private val findCuisines: FindCuisines,
) : FindCuisinesUseCase {
    @Transactional(readOnly = true)
    override fun execute(request: FindCuisinesQuery): List<FindCuisinesQueryResult> {
        return findCuisines.query(request.toInquiry()).map { it.toQuery() }
    }
}
