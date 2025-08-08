package com.reservation.company.port.input

import com.reservation.company.port.input.query.request.FindCompaniesByIdQuery
import com.reservation.company.port.input.query.response.FindCompaniesQueryResult

interface FindCompanyByIdNameUseCase {
    fun execute(request: FindCompaniesByIdQuery): FindCompaniesQueryResult
}
