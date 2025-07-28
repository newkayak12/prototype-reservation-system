package com.reservation.company.usecase

import com.reservation.company.port.input.FindCompaniesQuery
import com.reservation.company.port.input.FindCompaniesQuery.FindCompaniesQueryDto
import com.reservation.company.port.input.FindCompaniesQuery.FindCompaniesQueryResult
import com.reservation.company.port.output.FindCompanies
import com.reservation.config.annotations.UseCase
import org.springframework.transaction.annotation.Transactional

@UseCase
class FindCompaniesService(
    val findCompanies: FindCompanies,
) : FindCompaniesQuery {
    @Transactional(readOnly = true)
    override fun execute(request: FindCompaniesQueryDto): List<FindCompaniesQueryResult> {
        return findCompanies.query(request.toInquiry()).map {
            FindCompaniesQueryResult(
                it.id,
                it.brandName,
                it.brandUrl,
                it.representativeName,
                it.representativeMobile,
                it.businessNumber,
                it.corporateRegistrationNumber,
                it.phone,
                it.email,
                it.url,
                it.zipCode,
                it.address,
                it.detail,
            )
        }
    }
}
