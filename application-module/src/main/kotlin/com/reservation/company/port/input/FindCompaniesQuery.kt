package com.reservation.company.port.input

import com.reservation.company.port.output.FindCompanies.FindCompaniesInquiry

fun interface FindCompaniesQuery {
    fun execute(request: FindCompaniesQueryDto): List<FindCompaniesQueryResult>

    data class FindCompaniesQueryDto(
        val companyName: String?,
    ) {
        fun toInquiry() = FindCompaniesInquiry(companyName)
    }

    data class FindCompaniesQueryResult(
        val id: String,
        val brandName: String,
        val brandUrl: String,
        val representativeName: String,
        val representativeMobile: String,
        val businessNumber: String,
        val corporateRegistrationNumber: String,
        val phone: String,
        val email: String,
        val url: String,
        val zipCode: String,
        val address: String,
        val detail: String,
    )
}
