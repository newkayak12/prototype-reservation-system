package com.reservation.company

import com.reservation.company.vo.Address
import com.reservation.company.vo.Brand
import com.reservation.company.vo.Business
import com.reservation.company.vo.Contact
import com.reservation.company.vo.Representative

class Company(
    val id: String,
    var brand: Brand,
    val business: Business,
    var contact: Contact,
    var address: Address,
    var representative: Representative,
) {
    fun changeBrand(brand: Brand) {
        this.brand = brand
    }

    fun changeContact(contact: Contact) {
        this.contact = contact
    }

    fun changeAddress(address: Address) {
        this.address = address
    }

    fun changeRepresentative(representative: Representative) {
        this.representative = representative
    }
}
