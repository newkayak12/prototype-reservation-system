package com.reservation.user.general.attribute

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.reservation.config.restdoc.Body
import com.reservation.config.restdoc.PathParameter
import com.reservation.config.restdoc.RestDocuments
import com.reservation.config.security.TestSecurity
import com.reservation.rest.user.general.attribute.GeneralUserChangeNicknameController
import com.reservation.rest.user.general.request.GeneralUserChangeNicknameRequest
import com.reservation.user.self.port.input.ChangeGeneralUserNicknameCommand
import com.reservation.utilities.uuid.UuidGenerator
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.mockk.every
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureRestDocs
@ActiveProfiles(value = ["test"])
@Import(value = [TestSecurity::class])
@WebMvcTest(GeneralUserChangeNicknameController::class)
@ExtendWith(RestDocumentationExtension::class)
class GeneralUserChangeNicknameControllerTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
) : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @MockkBean
    private lateinit var changeGeneralUserNicknameCommand: ChangeGeneralUserNicknameCommand

    init {

        test("닉네임 변경 조건에 부합하여 닉네임이 변경된다.") {
            val url = "/api/v1/user/{id}/nickname"

            val request =
                GeneralUserChangeNicknameRequest(
                    "nickname",
                )

            every {
                changeGeneralUserNicknameCommand.execute(any())
            } returns true

            mockMvc.perform(
                patch(url, UuidGenerator.generate())
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer accessToken",
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
    }
}
