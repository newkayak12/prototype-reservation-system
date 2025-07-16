package com.reservation.company

import com.reservation.company.vo.Brand
import com.reservation.company.vo.Business
import com.reservation.company.vo.CompanyAddress
import com.reservation.company.vo.CompanyContact
import com.reservation.company.vo.Representative

class Company(
    val id: String,
    private var brand: Brand,
    private val business: Business,
    private var companyContact: CompanyContact,
    private var companyAddress: CompanyAddress,
    private var representative: Representative,
) {
    val brandName: String
        get() = brand.name
    val brandUrl: String
        get() = brand.url

    fun changeBrand(brand: Brand) {
        this.brand = brand
    }
}
