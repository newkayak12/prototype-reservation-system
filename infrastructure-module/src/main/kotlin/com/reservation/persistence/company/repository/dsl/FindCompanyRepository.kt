package com.reservation.persistence.company.repository.dsl

import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import com.reservation.company.port.output.FindCompany
import com.reservation.company.port.output.FindCompany.FindCompanyInquiry
import com.reservation.company.port.output.FindCompany.FindCompanyResult
import com.reservation.persistence.company.entity.QCompanyEntity.companyEntity
import org.springframework.stereotype.Component

@Component
class FindCompanyRepository(
    private val query: JPAQueryFactory,
) : FindCompany {
    override fun query(inquiry: FindCompanyInquiry): FindCompanyResult? {
        return query.select(
            Projections.constructor(
                FindCompanyResult::class.java,
                companyEntity.identifier,
                companyEntity.brandName,
                companyEntity.brandUrl,
                companyEntity.representativeName,
                companyEntity.representativeMobile,
                companyEntity.businessNumber,
                companyEntity.corporateRegistrationNumber,
                companyEntity.phone,
                companyEntity.email,
                companyEntity.url,
                companyEntity.zipCode,
                companyEntity.address,
                companyEntity.detail,
            ),
        )
            .from(companyEntity)
            .where(CompanyQuerySpec.identifierEqOrFalse(inquiry.id))
            .fetchOne()
    }
}
