package com.reservation.persistence.company.entity

import com.reservation.persistence.common.TimeBasedPrimaryKey
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Comment

@Table(
    catalog = "prototype_reservation",
    name = "company",
    indexes = [],
)
@Entity
class CompanyEntity(
    brandName: String,
    zipCode: String,
    address: String,
    detail: String,
    representativeName: String,
) : TimeBasedPrimaryKey() {
    @Column(name = "brand_name")
    @Comment("상호명")
    var brandName: String = brandName
        protected set

    @Column(name = "brand_url")
    @Comment("회사 홈페이지 URL")
    var brandUrl: String? = null
        protected set

    @Column(name = "business_number")
    @Comment("사업자 번호")
    var businessNumber: String? = null
        protected set

    @Column(name = "corporate_registration_number")
    @Comment("법인 등록번호")
    var corporateRegistrationNumber: String? = null
        protected set

    @Column(name = "phone")
    @Comment("전화번호")
    var phone: String? = null
        protected set

    @Column(name = "email")
    @Comment("이메일")
    var email: String? = null
        protected set

    @Column(name = "url")
    @Comment("URL")
    var url: String? = null
        protected set

    @Column(name = "zip_code")
    @Comment("우편번호")
    var zipCode: String = zipCode
        protected set

    @Column(name = "address")
    @Comment("주소")
    var address: String = address
        protected set

    @Column(name = "detail")
    @Comment("주소 상세")
    var detail: String = detail
        protected set

    @Column(name = "representative_name")
    @Comment("대표자")
    var representativeName: String = representativeName
        protected set

    @Column(name = "representative_mobile")
    @Comment("대표자 전화번호")
    var representativeMobile: String? = null
        protected set

    @Suppress("LongParameterList")
    constructor(
        brandName: String,
        brandUrl: String?,
        businessNumber: String?,
        corporateRegistrationNumber: String?,
        phone: String?,
        email: String?,
        url: String?,
        zipCode: String,
        address: String,
        detail: String,
        representativeName: String,
        representativeMobile: String?,
    ) : this(
        brandName,
        zipCode,
        address,
        detail,
        representativeName,
    ) {
        this.brandUrl = brandUrl
        this.businessNumber = businessNumber
        this.corporateRegistrationNumber = corporateRegistrationNumber
        this.phone = phone
        this.email = email
        this.url = url
        this.representativeMobile = representativeMobile
    }

    fun changeBrand(
        brandName: String,
        brandUrl: String?,
    ) {
        this.brandName = brandName
        this.brandUrl = brandUrl ?: this.brandUrl
    }

    fun changeBusiness(
        businessNumber: String?,
        corporateRegistrationNumber: String?,
    ) {
        this.businessNumber = businessNumber ?: this.businessNumber
        this.corporateRegistrationNumber =
            corporateRegistrationNumber ?: this.corporateRegistrationNumber
    }

    fun changeContact(
        phone: String?,
        email: String?,
        url: String?,
    ) {
        this.phone = phone ?: this.phone
        this.email = email ?: this.email
        this.url = url ?: this.url
    }

    fun changeAddress(
        zipCode: String,
        address: String,
        detail: String,
    ) {
        this.zipCode = zipCode
        this.address = address
        this.detail = detail
    }

    fun changeRepresentative(
        representativeName: String,
        representativeMobile: String?,
    ) {
        this.representativeName = representativeName
        this.representativeMobile = representativeMobile ?: this.representativeMobile
    }
}
