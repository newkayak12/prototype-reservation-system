package com.reservation.category.nationality.port.input

import com.reservation.category.nationality.port.input.query.request.FindNationalitiesByTitleQuery
import com.reservation.category.nationality.port.input.query.response.FindNationalitiesQueryResult

interface FindNationalitiesByTitleUseCase {
    fun execute(request: FindNationalitiesByTitleQuery): List<FindNationalitiesQueryResult>
}
