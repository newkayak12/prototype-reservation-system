package com.reservation.rest.resign

import com.reservation.config.MockMvcFactory
import com.reservation.config.SpringRestDocsKotestExtension
import com.reservation.config.restdoc.Body
import com.reservation.config.restdoc.PathParameter
import com.reservation.config.restdoc.RestDocuments
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.rest.resign.remove.ResignUserController
import com.reservation.user.resign.port.input.ResignUserUseCase
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.Matchers.equalTo
import org.springframework.http.HttpHeaders
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class ResignUserControllerTest : FunSpec(
    {

        val restDocsExtension = SpringRestDocsKotestExtension()
        extension(restDocsExtension)

        lateinit var mockMvc: MockMvc
        lateinit var resignUserUseCase: ResignUserUseCase

        beforeTest { testCase ->
            resignUserUseCase = mockk<ResignUserUseCase>()
            val controller = ResignUserController(resignUserUseCase)
            mockMvc =
                MockMvcFactory.buildMockMvc(
                    controller,
                    restDocsExtension.restDocumentation(testCase),
                )
        }

        test("사용자가 탈퇴한다.") {

            every {
                resignUserUseCase.execute(any())
            } returns true

            val id = UuidGenerator.generate()

            mockMvc.perform(
                delete(ResignUrl.RESIGN, id)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    ),
            )
                .andDo(print())
                .andExpectAll(
                    status().isResetContent,
                    jsonPath("$.result").isBoolean,
                    jsonPath("$.result", equalTo(true)),
                )
                .andDo(
                    RestDocuments(
                        identifier = "resign",
                        documentTags = listOf("resign_user"),
                        summary = "탈퇴",
                        pathParameter =
                            arrayOf(
                                PathParameter(
                                    name = "id",
                                    optional = false,
                                    description = "사용자 ID",
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
