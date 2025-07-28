package com.reservation.rest.company.self

import com.reservation.company.port.input.FindCompaniesUseCase
import com.reservation.rest.common.response.ListResponse
import com.reservation.rest.company.CompanyUrl
import com.reservation.rest.company.request.FindCompaniesRequest
import com.reservation.rest.company.response.FindCompaniesResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class FindCompaniesController(
    private val findCompaniesUseCase: FindCompaniesUseCase,
) {
    @GetMapping(CompanyUrl.COMPANIES_URL)
    fun findCompanies(request: FindCompaniesRequest): ListResponse<FindCompaniesResponse> =
        ListResponse.ok(
            findCompaniesUseCase.execute(request.toInquiry())
                .map {
                    FindCompaniesResponse(
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
                },
        )
}
