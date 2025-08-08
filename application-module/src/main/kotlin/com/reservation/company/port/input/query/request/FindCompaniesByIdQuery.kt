package com.reservation.company.port.input.query.request

import com.reservation.company.port.output.FindCompany.FindCompanyInquiry

data class FindCompaniesByIdQuery(
    val id: String?,
) {
    fun toInquiry() = FindCompanyInquiry(id)
}
