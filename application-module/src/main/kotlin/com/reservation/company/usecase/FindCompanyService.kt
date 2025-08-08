package com.reservation.company.usecase

import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.company.port.input.FindCompanyByIdNameUseCase
import com.reservation.company.port.input.query.request.FindCompaniesByIdQuery
import com.reservation.company.port.input.query.response.FindCompaniesQueryResult
import com.reservation.company.port.output.FindCompany
import com.reservation.config.annotations.UseCase
import org.springframework.transaction.annotation.Transactional

@UseCase
class FindCompanyService(
    val findCompany: FindCompany,
) : FindCompanyByIdNameUseCase {
    @Transactional(readOnly = true)
    override fun execute(request: FindCompaniesByIdQuery): FindCompaniesQueryResult {
        return findCompany.query(request.toInquiry())
            ?.let {
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
            ?: throw NoSuchPersistedElementException()
    }
}
