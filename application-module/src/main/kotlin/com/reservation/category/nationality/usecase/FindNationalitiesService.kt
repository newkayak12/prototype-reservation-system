package com.reservation.category.nationality.usecase

import com.reservation.category.nationality.port.input.FindNationalitiesByIdsUseCase
import com.reservation.category.nationality.port.input.FindNationalitiesByTitleUseCase
import com.reservation.category.nationality.port.input.query.request.FindNationalitiesByIdsQuery
import com.reservation.category.nationality.port.input.query.request.FindNationalitiesByTitleQuery
import com.reservation.category.nationality.port.input.query.response.FindNationalitiesQueryResult
import com.reservation.category.nationality.port.output.FindNationalities
import com.reservation.config.annotations.UseCase
import org.springframework.transaction.annotation.Transactional

@UseCase
class FindNationalitiesService(
    private val findNationalities: FindNationalities,
) : FindNationalitiesByTitleUseCase, FindNationalitiesByIdsUseCase {
    @Transactional(readOnly = true)
    override fun execute(
        request: FindNationalitiesByTitleQuery,
    ): List<FindNationalitiesQueryResult> {
        return findNationalities.query(request.toInquiry()).map { it.toQuery() }
    }

    @Transactional(readOnly = true)
    override fun execute(request: FindNationalitiesByIdsQuery): List<FindNationalitiesQueryResult> {
        return findNationalities.query(request.toInquiry()).map { it.toQuery() }
    }
}
