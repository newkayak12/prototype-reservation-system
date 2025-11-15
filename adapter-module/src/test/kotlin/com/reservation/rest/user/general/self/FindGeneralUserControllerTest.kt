package com.reservation.rest.user.general.self

import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.config.MockMvcFactory
import com.reservation.config.SpringRestDocsKotestExtension
import com.reservation.config.restdoc.Body
import com.reservation.config.restdoc.PathParameter
import com.reservation.config.restdoc.RestDocuments
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.rest.user.general.GeneralUserUrl
import com.reservation.rest.user.general.find.one.FindGeneralUserController
import com.reservation.user.self.port.input.FindGeneralUserUseCase
import com.reservation.user.self.port.input.query.response.FindGeneralUserQueryResult
import com.reservation.utilities.generator.uuid.UuidGenerator
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

class FindGeneralUserControllerTest : FunSpec(
    {
        val restDocsExtension = SpringRestDocsKotestExtension()
        extension(restDocsExtension)

        lateinit var mockMvc: MockMvc
        lateinit var findGeneralUserUseCase: FindGeneralUserUseCase

        beforeTest { testCase ->
            findGeneralUserUseCase = mockk<FindGeneralUserUseCase>()
            val controller = FindGeneralUserController(findGeneralUserUseCase)
            mockMvc =
                MockMvcFactory.buildMockMvc(
                    controller,
                    restDocsExtension.restDocumentation(testCase),
                )
        }

        test("사용자를 찾을 수 없음") {

            val id = UuidGenerator.generate()

            every {
                findGeneralUserUseCase.execute(any())
            } throws NoSuchPersistedElementException()

            mockMvc.perform(
                get(GeneralUserUrl.FIND_USER, id)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    ),
            )
                .andDo(print())
                .andExpectAll(
                    status().isBadRequest,
                )
        }

        test("사용자를 정상적으로 찾고 리턴한다.") {

            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val id = UuidGenerator.generate()

            every {
                findGeneralUserUseCase.execute(any())
            } returns
                pureMonkey.giveMeBuilder<FindGeneralUserQueryResult>()
                    .set("id", id)
                    .sample()

            mockMvc.perform(
                get(GeneralUserUrl.FIND_USER, id)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    ),
            )
                .andDo(print())
                .andExpectAll(
                    status().isOk,
                    jsonPath("$.id").exists(),
                    jsonPath("$.loginId").exists(),
                    jsonPath("$.email").exists(),
                    jsonPath("$.nickname").exists(),
                    jsonPath("$.mobile").exists(),
                    jsonPath("$.userStatus").exists(),
                )
                .andDo(
                    RestDocuments(
                        identifier = "findGeneralUser",
                        documentTags = listOf("general_user"),
                        summary = "일반 회원 조회",
                        pathParameter =
                            arrayOf(
                                PathParameter(name = "id", optional = false, description = "id"),
                            ),
                        responseBody =
                            arrayOf(
                                Body("id", STRING, false, "id"),
                                Body("loginId", STRING, false, "회원 아이디"),
                                Body("email", STRING, false, "이메일"),
                                Body("nickname", STRING, false, "닉네임"),
                                Body("mobile", STRING, true, "전화번호"),
                                Body("userStatus", STRING, false, "사용자 상태"),
                            ),
                    )
                        .authorizedRequestHeader()
                        .create(),
                )
        }
    },
)
