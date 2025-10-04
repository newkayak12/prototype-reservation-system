package com.reservation.rest.user.general.find.password

import com.fasterxml.jackson.databind.ObjectMapper
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.ninjasquad.springmockk.MockkBean
import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.config.restdoc.Body
import com.reservation.config.restdoc.RestDocuments
import com.reservation.config.security.TestSecurity
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.rest.user.general.GeneralUserUrl
import com.reservation.rest.user.general.request.FindGeneralUserPasswordRequest
import com.reservation.rest.user.general.sign.find.password.FindGeneralUserPasswordController
import com.reservation.user.self.port.input.FindGeneralUserPasswordUseCase
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
import org.springframework.restdocs.payload.JsonFieldType.BOOLEAN
import org.springframework.restdocs.payload.JsonFieldType.STRING
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureRestDocs
@ActiveProfiles(value = ["test"])
@Import(value = [TestSecurity::class])
@WebMvcTest(FindGeneralUserPasswordController::class)
@ExtendWith(RestDocumentationExtension::class)
class FindGeneralUserPasswordControllerTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
) : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @MockkBean
    private lateinit var findGeneralUserPasswordUseCase: FindGeneralUserPasswordUseCase

    init {

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
    }
}
