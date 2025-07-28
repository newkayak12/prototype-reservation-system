package com.reservation.category.nationality.port.input

import com.reservation.category.nationality.port.input.query.request.FindNationalitiesQuery
import com.reservation.category.nationality.port.input.query.response.FindNationalitiesQueryResult

interface FindNationalitiesUseCase {
    fun execute(request: FindNationalitiesQuery): List<FindNationalitiesQueryResult>
}
