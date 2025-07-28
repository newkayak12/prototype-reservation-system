package com.reservation.company.port.input

import com.reservation.company.port.input.query.request.FindCompaniesQuery
import com.reservation.company.port.input.query.response.FindCompaniesQueryResult

interface FindCompaniesUseCase {
    fun execute(request: FindCompaniesQuery): List<FindCompaniesQueryResult>
}
