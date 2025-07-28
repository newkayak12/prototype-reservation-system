package com.reservation.category.nationality.usecase

import com.reservation.category.nationality.port.input.FindNationalitiesUseCase
import com.reservation.category.nationality.port.input.query.request.FindNationalitiesQuery
import com.reservation.category.nationality.port.input.query.response.FindNationalitiesQueryResult
import com.reservation.category.nationality.port.output.FindNationalities
import com.reservation.config.annotations.UseCase
import org.springframework.transaction.annotation.Transactional

@UseCase
class FindNationalitiesService(
    private val findNationalities: FindNationalities,
) : FindNationalitiesUseCase {
    @Transactional(readOnly = true)
    override fun execute(request: FindNationalitiesQuery): List<FindNationalitiesQueryResult> {
        return findNationalities.query(request.toInquiry()).map { it.toQuery() }
    }
}
