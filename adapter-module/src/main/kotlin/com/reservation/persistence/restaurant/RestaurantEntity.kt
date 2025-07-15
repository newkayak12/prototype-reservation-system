package com.reservation.persistence.restaurant

import com.reservation.persistence.common.TimeBasedPrimaryKey
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.math.BigDecimal

@Table(
    catalog = "prototype_reservation",
    name = "restaurant",
    indexes = [],
)
@Entity
@Suppress("LongParameterList")
class RestaurantEntity(
    companyId: String,
    name: String,
    introduce: String,
    phone: String,
    zipCode: String,
    address: String,
    detail: String,
    latitude: BigDecimal,
    longitude: BigDecimal,
) : TimeBasedPrimaryKey() {
    @Column(name = "company_id")
    private var companyId: String = companyId

    @Column(name = "name")
    private var name: String = name

    @Column(name = "introduce")
    private var introduce: String = introduce

    @Column(name = "phone")
    private var phone: String = phone

    @Column(name = "zip_code")
    private var zipCode: String = zipCode

    @Column(name = "address")
    private var address: String = address

    @Column(name = "detail")
    private var detail: String = detail

    @Column(name = "latitude", precision = 8, scale = 5)
    private var latitude: BigDecimal = latitude

    @Column(name = "longitude", precision = 8, scale = 5)
    private var longitude: BigDecimal = longitude

    @OneToMany(
        mappedBy = "restaurant",
        cascade = [ CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE ],
        fetch = FetchType.LAZY,
    )
    private var weekDays = mutableListOf<RestaurantWorkingDayEntity>()
}
