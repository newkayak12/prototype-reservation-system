package com.reservation.company.policy.formats

data class CreateCompanyForm(
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
