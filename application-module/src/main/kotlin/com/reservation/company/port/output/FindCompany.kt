package com.reservation.company.port.output

interface FindCompany {
    fun query(inquiry: FindCompanyInquiry): FindCompanyResult?

    data class FindCompanyInquiry(
        val id: String?,
    )

    data class FindCompanyResult(
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
