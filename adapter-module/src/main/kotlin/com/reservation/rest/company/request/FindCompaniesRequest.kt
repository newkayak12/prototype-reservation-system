package com.reservation.rest.company.request

import com.reservation.company.port.input.FindCompaniesQuery.FindCompaniesQueryDto

data class FindCompaniesRequest(
    val companyName: String?,
) {
    fun toInquiry() = FindCompaniesQueryDto(companyName)
}
