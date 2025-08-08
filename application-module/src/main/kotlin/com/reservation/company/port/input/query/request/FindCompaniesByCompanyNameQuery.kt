package com.reservation.company.port.input.query.request

import com.reservation.company.port.output.FindCompanies.FindCompaniesInquiry

data class FindCompaniesByCompanyNameQuery(
    val companyName: String?,
) {
    fun toInquiry() = FindCompaniesInquiry(companyName)
}
