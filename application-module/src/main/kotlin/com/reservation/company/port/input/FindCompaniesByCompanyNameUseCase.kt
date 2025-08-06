package com.reservation.company.port.input

import com.reservation.company.port.input.query.request.FindCompaniesByCompanyNameQuery
import com.reservation.company.port.input.query.response.FindCompaniesQueryResult

interface FindCompaniesByCompanyNameUseCase {
    fun execute(request: FindCompaniesByCompanyNameQuery): List<FindCompaniesQueryResult>
}
