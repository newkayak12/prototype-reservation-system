package com.reservation.company

import com.navercorp.fixturemonkey.kotlin.giveMe
import com.ninjasquad.springmockk.MockkBean
import com.reservation.company.port.input.FindCompaniesQuery
import com.reservation.company.port.input.FindCompaniesQuery.FindCompaniesQueryResult
import com.reservation.config.restdoc.Body
import com.reservation.config.restdoc.Query
import com.reservation.config.restdoc.RestDocuments
import com.reservation.config.security.TestSecurity
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.rest.company.CompanyUrl
import com.reservation.rest.company.self.FindCompaniesController
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.mockk.every
import net.jqwik.api.Arbitraries
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.payload.JsonFieldType.STRING
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureRestDocs
@ActiveProfiles(value = ["test"])
@Import(value = [TestSecurity::class])
@WebMvcTest(FindCompaniesController::class)
@ExtendWith(RestDocumentationExtension::class)
class FindCompaniesControllerTest(
    private val mockMvc: MockMvc,
) : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @MockkBean
    private lateinit var findCompaniesQuery: FindCompaniesQuery

    init {
        test("회사를 조회하고 총 55건의 결과를 반환받는다.") {

            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val queryResult = pureMonkey.giveMe<FindCompaniesQueryResult>(55)

            every {
                findCompaniesQuery.execute(any())
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
    }
}
