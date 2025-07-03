package com.reservation.category.nationality.usecase

import com.reservation.category.nationality.port.input.FindNationalitiesQuery
import com.reservation.category.nationality.port.input.FindNationalitiesQuery.FindNationalitiesQueryDto
import com.reservation.category.nationality.port.input.FindNationalitiesQuery.FindNationalitiesQueryResult
import com.reservation.category.nationality.port.output.FindNationalities
import com.reservation.config.annotations.UseCase
import org.springframework.transaction.annotation.Transactional

@UseCase
class FindNationalitiesUseCase(
    private val findNationalities: FindNationalities,
) : FindNationalitiesQuery {
    @Transactional(readOnly = true)
    override fun execute(request: FindNationalitiesQueryDto): List<FindNationalitiesQueryResult> {
        return findNationalities.query(request.toInquiry()).map { it.toQuery() }
    }
}
