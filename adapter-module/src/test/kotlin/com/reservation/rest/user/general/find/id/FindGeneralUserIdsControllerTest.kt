package com.reservation.rest.user.general.find.id

import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.reservation.config.MockMvcFactory
import com.reservation.config.SpringRestDocsKotestExtension
import com.reservation.config.restdoc.Body
import com.reservation.config.restdoc.Query
import com.reservation.config.restdoc.RestDocuments
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.rest.user.general.GeneralUserUrl
import com.reservation.rest.user.general.sign.find.id.FindGeneralUserIdsController
import com.reservation.user.self.port.input.FindGeneralUserIdsUseCase
import com.reservation.user.self.port.input.query.response.FindGeneralUserIdQueryResult
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpHeaders
import org.springframework.restdocs.payload.JsonFieldType.STRING
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class FindGeneralUserIdsControllerTest : FunSpec(
    {
        val restDocsExtension = SpringRestDocsKotestExtension()
        extension(restDocsExtension)

        lateinit var mockMvc: MockMvc
        lateinit var findGeneralUserIdsUseCase: FindGeneralUserIdsUseCase

        beforeTest { testCase ->
            findGeneralUserIdsUseCase = mockk<FindGeneralUserIdsUseCase>()
            val controller = FindGeneralUserIdsController(findGeneralUserIdsUseCase)
            mockMvc =
                MockMvcFactory.buildMockMvc(
                    controller,
                    restDocsExtension.restDocumentation(testCase),
                )
        }

        test("올바르지 않은 이메일을 입력하여 아이디를 찾을 수 없다.") {

            every {
                findGeneralUserIdsUseCase.execute(any())
            } returns emptyList()

            mockMvc.perform(
                get(GeneralUserUrl.FIND_LOST_LOGIN_ID)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .queryParam("email", CommonlyUsedArbitraries.emailArbitrary.sample()),
            )
                .andDo(print())
                .andExpectAll(
                    status().isOk,
                    jsonPath("$.list").isArray,
                    jsonPath("$.list").isEmpty,
                )
        }

        test("올바른 이메일을 입력하여 2개의 아이디를 리스트업 한다.") {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

            every {
                findGeneralUserIdsUseCase.execute(any())
            } returns
                pureMonkey.giveMeBuilder<FindGeneralUserIdQueryResult>()
                    .set("userId", CommonlyUsedArbitraries.loginIdArbitrary.sample())
                    .sampleList(2)

            mockMvc.perform(
                get(GeneralUserUrl.FIND_LOST_LOGIN_ID)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .queryParam("email", CommonlyUsedArbitraries.emailArbitrary.sample()),
            )
                .andDo(print())
                .andExpectAll(
                    status().isOk,
                    jsonPath("$.list").isArray,
                    jsonPath("$.list").isNotEmpty,
                    jsonPath("$.list.size()").value(2),
                )
                .andDo(
                    RestDocuments(
                        identifier = "findLostLoginId",
                        documentTags = listOf("general_user"),
                        summary = "일반 회원 아이디 찾기",
                        query =
                            arrayOf(
                                Query("email", false, "이메일"),
                            ),
                        responseBody =
                            arrayOf(
                                Body(
                                    "list[].userId",
                                    STRING,
                                    false,
                                    "아이디 찾기 결과",
                                ),
                            ),
                    )
                        .authorizedRequestHeader()
                        .create(),
                )
        }
    },
)
