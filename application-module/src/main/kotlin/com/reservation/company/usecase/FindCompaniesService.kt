package com.reservation.company.usecase

import com.reservation.company.port.input.FindCompaniesByCompanyNameUseCase
import com.reservation.company.port.input.query.request.FindCompaniesByCompanyNameQuery
import com.reservation.company.port.input.query.response.FindCompaniesQueryResult
import com.reservation.company.port.output.FindCompanies
import com.reservation.config.annotations.UseCase
import org.springframework.transaction.annotation.Transactional

@UseCase
class FindCompaniesService(
    val findCompanies: FindCompanies,
) : FindCompaniesByCompanyNameUseCase {
    @Transactional(readOnly = true)
    override fun execute(request: FindCompaniesByCompanyNameQuery): List<FindCompaniesQueryResult> {
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
