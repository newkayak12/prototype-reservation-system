package com.reservation.rest.company

import com.navercorp.fixturemonkey.kotlin.giveMe
import com.reservation.company.port.input.FindCompaniesByCompanyNameUseCase
import com.reservation.company.port.input.query.response.FindCompaniesQueryResult
import com.reservation.config.MockMvcFactory
import com.reservation.config.SpringRestDocsKotestExtension
import com.reservation.config.restdoc.Body
import com.reservation.config.restdoc.Query
import com.reservation.config.restdoc.RestDocuments
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.rest.company.self.FindCompaniesController
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import net.jqwik.api.Arbitraries
import org.springframework.http.HttpHeaders
import org.springframework.restdocs.payload.JsonFieldType.STRING
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class FindCompaniesControllerTest : FunSpec(
    {

        val restDocsExtension = SpringRestDocsKotestExtension()
        extension(restDocsExtension)

        lateinit var mockMvc: MockMvc
        lateinit var findCompaniesByCompanyNameUseCase: FindCompaniesByCompanyNameUseCase

        beforeTest { testCase ->
            findCompaniesByCompanyNameUseCase = mockk<FindCompaniesByCompanyNameUseCase>()
            val controller = FindCompaniesController(findCompaniesByCompanyNameUseCase)
            mockMvc =
                MockMvcFactory.buildMockMvc(
                    controller,
                    restDocsExtension.restDocumentation(testCase),
                )
        }

        test("회사를 조회하고 총 55건의 결과를 반환받는다.") {

            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val queryResult = pureMonkey.giveMe<FindCompaniesQueryResult>(55)

            every {
                findCompaniesByCompanyNameUseCase.execute(any())
            } returns queryResult

            mockMvc.perform(
                get(CompanyUrl.COMPANIES_URL)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .queryParam(
                        "companyName",
                        Arbitraries
                            .strings()
                            .ofMinLength(3)
                            .ofMaxLength(10)
                            .ascii()
                            .sample(),
                    ),
            )
                .andDo(print())
                .andExpectAll(
                    status().isOk,
                    jsonPath("$.list").isArray,
                    jsonPath("$.list").isNotEmpty,
                    jsonPath("$.list[*]").isNotEmpty,
                )
                .andDo(
                    RestDocuments(
                        identifier = "findCompanies",
                        documentTags = listOf("companey"),
                        summary = "회사 조회",
                        description = "회사 전체를 조회합니다.",
                        query =
                            arrayOf(
                                Query("companyName", true, "회사"),
                            ),
                        responseBody =
                            arrayOf(
                                Body("list[].id", STRING, true, "식별키"),
                                Body("list[].brandName", STRING, true, "회사명"),
                                Body("list[].brandUrl", STRING, false, "브랜드 URL"),
                                Body("list[].representativeName", STRING, true, "대표명"),
                                Body("list[].representativeMobile", STRING, false, "대표자 전화번호"),
                                Body("list[].businessNumber", STRING, true, "사업자 번호"),
                                Body(
                                    "list[].corporateRegistrationNumber",
                                    STRING,
                                    false,
                                    "법인 등록번호",
                                ),
                                Body("list[].phone", STRING, false, "전화번호"),
                                Body("list[].email", STRING, false, "이메일"),
                                Body("list[].url", STRING, true, "URL"),
                                Body("list[].zipCode", STRING, true, "우편번호"),
                                Body("list[].address", STRING, true, "주소"),
                                Body("list[].detail", STRING, true, "상세 주소"),
                            ),
                    )
                        .authorizedRequestHeader()
                        .create(),
                )
        }
    },
)
