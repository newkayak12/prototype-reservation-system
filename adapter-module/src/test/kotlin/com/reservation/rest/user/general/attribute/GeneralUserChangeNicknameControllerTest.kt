package com.reservation.rest.user.general.attribute

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.reservation.config.MockMvcFactory
import com.reservation.config.SpringRestDocsKotestExtension
import com.reservation.config.restdoc.Body
import com.reservation.config.restdoc.PathParameter
import com.reservation.config.restdoc.RestDocuments
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.rest.user.general.attribute.change.nickname.GeneralUserChangeNicknameController
import com.reservation.rest.user.general.request.GeneralUserChangeNicknameRequest
import com.reservation.user.self.port.input.ChangeGeneralUserNicknameUseCase
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.Matchers.equalTo
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class GeneralUserChangeNicknameControllerTest : FunSpec(
    {
        val restDocsExtension = SpringRestDocsKotestExtension()
        extension(restDocsExtension)

        lateinit var mockMvc: MockMvc
        lateinit var changeGeneralUserNicknameUseCase: ChangeGeneralUserNicknameUseCase

        beforeTest { testCase ->
            changeGeneralUserNicknameUseCase = mockk<ChangeGeneralUserNicknameUseCase>()
            val controller = GeneralUserChangeNicknameController(changeGeneralUserNicknameUseCase)
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

        test("닉네임 변경 조건에 부합하여 닉네임이 변경된다.") {
            val url = "/api/v1/user/{id}/nickname"

            val request =
                GeneralUserChangeNicknameRequest(
                    "new_nickname",
                )

            every {
                changeGeneralUserNicknameUseCase.execute(any())
            } returns true

            mockMvc.perform(
                patch(url, UuidGenerator.generate())
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(objectMapper.writeValueAsString(request)),
            )
                .andDo(print())
                .andExpectAll(
                    status().isResetContent,
                    jsonPath("$.result").isBoolean,
                    jsonPath("$.result", equalTo(true)),
                )
                .andDo(
                    RestDocuments(
                        identifier = "changeNickname",
                        documentTags = listOf("general_user"),
                        summary = "닉네임 변경",
                        pathParameter =
                            arrayOf(
                                PathParameter(
                                    name = "id",
                                    optional = false,
                                    description = "사용자 ID",
                                ),
                            ),
                        requestBody =
                            arrayOf(
                                Body(
                                    name = "nickname",
                                    jsonType = JsonFieldType.STRING,
                                    optional = false,
                                    description = "변경할 닉네임",
                                ),
                            ),
                        responseBody =
                            arrayOf(
                                Body(
                                    name = "result",
                                    jsonType = JsonFieldType.BOOLEAN,
                                    optional = false,
                                    description = "결과",
                                ),
                            ),
                    )
                        .authorizedRequestHeader()
                        .create(),
                )
        }
    },
)
