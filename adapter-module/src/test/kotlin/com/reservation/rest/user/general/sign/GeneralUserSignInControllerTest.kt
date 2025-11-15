package com.reservation.rest.user.general.sign

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.authenticate.port.input.AuthenticateGeneralUserUseCase
import com.reservation.authenticate.port.input.query.response.AuthenticateGeneralUserQueryResult
import com.reservation.config.MockMvcFactory
import com.reservation.config.SpringRestDocsKotestExtension
import com.reservation.config.restdoc.Body
import com.reservation.config.restdoc.RestDocuments
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.rest.user.general.GeneralUserUrl
import com.reservation.rest.user.general.request.GeneralUserLoginRequest
import com.reservation.rest.user.general.sign.income.GeneralUserSignInController
import io.kotest.core.spec.style.FunSpec
import io.kotest.data.forAll
import io.kotest.data.row
import io.mockk.every
import io.mockk.mockk
import net.jqwik.api.Arbitraries
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put
import org.springframework.restdocs.payload.JsonFieldType.STRING
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class GeneralUserSignInControllerTest : FunSpec(
    {

        val restDocsExtension = SpringRestDocsKotestExtension()
        extension(restDocsExtension)

        lateinit var mockMvc: MockMvc
        lateinit var authenticateGeneralUserUseCase: AuthenticateGeneralUserUseCase

        beforeTest { testCase ->
            authenticateGeneralUserUseCase = mockk<AuthenticateGeneralUserUseCase>()
            val controller = GeneralUserSignInController(authenticateGeneralUserUseCase)
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

        test("로그인을 시도했으나 jakarta validation에 부합하지 않아 실패한다.") {

            forAll(
                row("loginId"),
                row("password"),
            ) { emptyParameter ->

                val request =
                    GeneralUserLoginRequest(
                        if (emptyParameter == "loginId") "" else Arbitraries.strings().sample(),
                        if (emptyParameter == "password") "" else Arbitraries.strings().sample(),
                    )

                mockMvc.perform(
                    put(GeneralUserUrl.USER_SIGN_IN)
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
                authenticateGeneralUserUseCase.execute(any())
            } returns jakartaMonkey.giveMeOne<AuthenticateGeneralUserQueryResult>()

            mockMvc.perform(
                put(GeneralUserUrl.USER_SIGN_IN)
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
    },
)
