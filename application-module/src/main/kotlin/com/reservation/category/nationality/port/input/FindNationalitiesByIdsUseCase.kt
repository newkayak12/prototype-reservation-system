package com.reservation.category.nationality.port.input

import com.reservation.category.nationality.port.input.query.request.FindNationalitiesByIdsQuery
import com.reservation.category.nationality.port.input.query.response.FindNationalitiesQueryResult

interface FindNationalitiesByIdsUseCase {
    fun execute(request: FindNationalitiesByIdsQuery): List<FindNationalitiesQueryResult>
}
