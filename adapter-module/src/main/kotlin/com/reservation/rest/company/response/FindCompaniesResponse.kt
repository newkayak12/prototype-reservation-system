package com.reservation.rest.company.response

data class FindCompaniesResponse(
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
