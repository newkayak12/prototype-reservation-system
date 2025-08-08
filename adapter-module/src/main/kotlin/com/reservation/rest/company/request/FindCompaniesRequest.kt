package com.reservation.rest.company.request

import com.reservation.company.port.input.query.request.FindCompaniesByCompanyNameQuery

data class FindCompaniesRequest(
    val companyName: String?,
) {
    fun toInquiry() = FindCompaniesByCompanyNameQuery(companyName)
}
