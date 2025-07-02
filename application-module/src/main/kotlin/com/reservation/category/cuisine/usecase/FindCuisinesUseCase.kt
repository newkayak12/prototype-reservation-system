package com.reservation.category.cuisine.usecase

import com.reservation.category.cuisine.port.input.FindCuisinesQuery
import com.reservation.category.cuisine.port.input.FindCuisinesQuery.FindCuisinesQueryDto
import com.reservation.category.cuisine.port.input.FindCuisinesQuery.FindCuisinesQueryResult
import com.reservation.category.cuisine.port.output.FindCuisines
import com.reservation.config.annotations.UseCase
import org.springframework.transaction.annotation.Transactional

@UseCase
class FindCuisinesUseCase(
    private val findCuisines: FindCuisines,
) : FindCuisinesQuery {
    @Transactional(readOnly = true)
    override fun execute(request: FindCuisinesQueryDto): List<FindCuisinesQueryResult> {
        return findCuisines.query(request.toInquiry()).map { it.toQuery() }
    }
}
