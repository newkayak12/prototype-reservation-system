package com.reservation.company

import com.reservation.company.vo.Address
import com.reservation.company.vo.Brand
import com.reservation.company.vo.Business
import com.reservation.company.vo.Contact
import com.reservation.company.vo.Representative

class Company(
    val id: String,
    private var brand: Brand,
    private val business: Business,
    private var contact: Contact,
    private var address: Address,
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
