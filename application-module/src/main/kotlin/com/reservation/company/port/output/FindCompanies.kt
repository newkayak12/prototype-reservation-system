package com.reservation.company.port.output

interface FindCompanies {
    fun query(inquiry: FindCompaniesInquiry): List<FindCompaniesResult>

    data class FindCompaniesInquiry(
        val companyName: String?,
    )

    data class FindCompaniesResult(
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
