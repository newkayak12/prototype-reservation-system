package com.reservation.user.general

import com.fasterxml.jackson.databind.ObjectMapper
import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.ninjasquad.springmockk.MockkBean
import com.reservation.config.restdoc.Body
import com.reservation.config.restdoc.RestDocuments
import com.reservation.config.security.TestSecurity
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.rest.user.general.GeneralUserUrl
import com.reservation.rest.user.general.request.GeneralUserLoginRequest
import com.reservation.rest.user.general.sign.GeneralUserSignInController
import com.reservation.user.self.port.input.AuthenticateGeneralUserQuery
import com.reservation.user.self.port.input.AuthenticateGeneralUserQuery.AuthenticateGeneralUserQueryResult
import io.kotest.core.spec.style.FunSpec
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.extensions.spring.SpringExtension
import io.mockk.every
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put
import org.springframework.restdocs.payload.JsonFieldType.STRING
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureRestDocs
@ActiveProfiles(value = ["test"])
@Import(value = [TestSecurity::class])
@WebMvcTest(GeneralUserSignInController::class)
@ExtendWith(RestDocumentationExtension::class)
class GeneralUserSignInControllerTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
) : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @MockkBean
    private lateinit var authenticateGeneralUserQuery: AuthenticateGeneralUserQuery

    init {

        test("로그인을 시도했으나 jakarta validation에 부합하지 않아 실패한다.") {

            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

            forAll(
                row("loginId"),
                row("password"),
            ) { emptyParameter ->

                val request =
                    pureMonkey.giveMeBuilder<GeneralUserLoginRequest>()
                        .set(emptyParameter, "")
                        .sample()

                mockMvc.perform(
                    put(GeneralUserUrl.GENERAL_USER_SIGN_IN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                )
                    .andDo(print())
                    .andExpectAll(
                        status().is4xxClientError,
                    )
            }
        }

        test("로그인을 시도했고 성공한다.") {

            val jakartaMonkey = FixtureMonkeyFactory.giveMeJakartaMonkey().build()

            val request = jakartaMonkey.giveMeOne<GeneralUserLoginRequest>()

            every {
                authenticateGeneralUserQuery.execute(any())
            } returns jakartaMonkey.giveMeOne<AuthenticateGeneralUserQueryResult>()

            mockMvc.perform(
                put(GeneralUserUrl.GENERAL_USER_SIGN_IN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            )
                .andExpectAll(
                    status().is2xxSuccessful,
                )
                .andDo(print())
                .andDo(
                    RestDocuments(
                        identifier = "signIn",
                        documentTags = listOf("general_user"),
                        summary = "일반 회원 로그인",
                        requestBody =
                            arrayOf(
                                Body("loginId", STRING, false, "아이디"),
                                Body("password", STRING, false, "비밀번호"),
                            ),
                        responseBody =
                            arrayOf(
                                Body("accessToken", STRING, false, "액세스 토큰"),
                            ),
                    )
                        .create(),
                )
        }
    }
}
