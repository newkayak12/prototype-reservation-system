package com.reservation.rest.user.general.find.password

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.config.MockMvcFactory
import com.reservation.config.SpringRestDocsKotestExtension
import com.reservation.config.restdoc.Body
import com.reservation.config.restdoc.RestDocuments
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.rest.user.general.GeneralUserUrl
import com.reservation.rest.user.general.request.FindGeneralUserPasswordRequest
import com.reservation.rest.user.general.sign.find.password.FindGeneralUserPasswordController
import com.reservation.user.self.port.input.FindGeneralUserPasswordUseCase
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.Matchers.equalTo
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.payload.JsonFieldType.BOOLEAN
import org.springframework.restdocs.payload.JsonFieldType.STRING
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class FindGeneralUserPasswordControllerTest : FunSpec(
    {
        val restDocsExtension = SpringRestDocsKotestExtension()
        extension(restDocsExtension)

        lateinit var mockMvc: MockMvc
        lateinit var findGeneralUserPasswordUseCase: FindGeneralUserPasswordUseCase

        beforeTest { testCase ->
            findGeneralUserPasswordUseCase = mockk<FindGeneralUserPasswordUseCase>()
            val controller = FindGeneralUserPasswordController(findGeneralUserPasswordUseCase)
            mockMvc =
                MockMvcFactory.buildMockMvc(
                    controller,
                    restDocsExtension.restDocumentation(testCase),
                )
        }

        val objectMapper =
            ObjectMapper()
                .registerModule(JavaTimeModule())
                .registerModules(KotlinModule.Builder().build())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        test("올바르지 않은 아이디, 이메일을 입력하여 아이디를 찾을 수 없다.") {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val request = pureMonkey.giveMeOne<FindGeneralUserPasswordRequest>()

            every {
                findGeneralUserPasswordUseCase.execute(any())
            } throws NoSuchPersistedElementException()

            mockMvc.perform(
                patch(GeneralUserUrl.FIND_LOST_PASSWORD)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(objectMapper.writeValueAsString(request)),
            )
                .andDo(print())
                .andExpectAll(
                    status().isBadRequest,
                )
        }

        test("비밀번호를 변경하고 이메일을 발송한다.") {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val request = pureMonkey.giveMeOne<FindGeneralUserPasswordRequest>()

            every {
                findGeneralUserPasswordUseCase.execute(any())
            } returns true

            mockMvc.perform(
                patch(GeneralUserUrl.FIND_LOST_PASSWORD)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(objectMapper.writeValueAsString(request)),
            )
                .andDo(print())
                .andExpectAll(
                    status().isOk,
                    jsonPath("$.result").isBoolean,
                    jsonPath("$.result", equalTo(true)),
                )
                .andDo(
                    RestDocuments(
                        identifier = "findLostPassword",
                        documentTags = listOf("general_user"),
                        summary = "일반 회원 비밀번호 찾기",
                        requestBody =
                            arrayOf(
                                Body("loginId", STRING, false, "아이디"),
                                Body("email", STRING, false, "이메일"),
                            ),
                        responseBody =
                            arrayOf(
                                Body(
                                    "result",
                                    BOOLEAN,
                                    false,
                                    "비밀번호 찾기 결과",
                                ),
                            ),
                    )
                        .authorizedRequestHeader()
                        .create(),
                )
        }
    },
)
