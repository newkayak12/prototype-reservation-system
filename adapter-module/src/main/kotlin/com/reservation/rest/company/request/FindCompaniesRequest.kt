package com.reservation.rest.company.request

import com.reservation.company.port.input.query.request.FindCompaniesQuery

data class FindCompaniesRequest(
    val companyName: String?,
) {
    fun toInquiry() = FindCompaniesQuery(companyName)
}
