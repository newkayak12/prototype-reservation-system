package com.reservation.persistence.company.repository.dsl

import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import com.reservation.company.port.input.FindCompaniesQuery.FindCompaniesQueryResult
import com.reservation.company.port.output.FindCompanies
import com.reservation.company.port.output.FindCompanies.FindCompaniesInquiry
import com.reservation.persistence.company.entity.QCompanyEntity.companyEntity
import org.springframework.stereotype.Component

@Component
class FindCompanies(
    private val query: JPAQueryFactory,
) : FindCompanies {
    override fun query(inquiry: FindCompaniesInquiry): List<FindCompaniesQueryResult> {
        return query.select(
            Projections.constructor(
                FindCompaniesQueryResult::class.java,
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
            .where(
                CompanyQuerySpec.brandNameContains(inquiry.companyName),
            )
            .fetch()
    }
}
