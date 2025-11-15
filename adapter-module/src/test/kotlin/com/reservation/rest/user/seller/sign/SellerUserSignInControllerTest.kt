package com.reservation.rest.user.seller.sign

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.authenticate.port.input.AuthenticateSellerUserUseCase
import com.reservation.authenticate.port.input.query.response.AuthenticateSellerUserQueryResult
import com.reservation.config.MockMvcFactory
import com.reservation.config.SpringRestDocsKotestExtension
import com.reservation.config.restdoc.Body
import com.reservation.config.restdoc.RestDocuments
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.rest.user.general.GeneralUserUrl
import com.reservation.rest.user.general.request.GeneralUserLoginRequest
import com.reservation.rest.user.seller.SellerUserUrl
import com.reservation.rest.user.seller.request.SellerUserLoginRequest
import com.reservation.rest.user.seller.sign.income.SellerUserSignInController
import io.kotest.core.spec.style.FunSpec
import io.kotest.data.row
import io.mockk.every
import io.mockk.mockk
import net.jqwik.api.Arbitraries
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put
import org.springframework.restdocs.payload.JsonFieldType.STRING
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class SellerUserSignInControllerTest : FunSpec(
    {
        val restDocsExtension = SpringRestDocsKotestExtension()
        extension(restDocsExtension)

        lateinit var mockMvc: MockMvc

        lateinit var authenticateSellerUserUseCase: AuthenticateSellerUserUseCase

        beforeTest { testCase ->
            authenticateSellerUserUseCase = mockk<AuthenticateSellerUserUseCase>()
            val controller = SellerUserSignInController(authenticateSellerUserUseCase)
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
        test("로그인 시도했으나 JakartaAnnotation에서 걸려 실패한다.") {
            io.kotest.data.forAll(
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
                    .andDo(MockMvcResultHandlers.print())
                    .andExpectAll(
                        status().is4xxClientError,
                    )
            }
        }

        test("로그인을 시도했고 성공한다.") {

            val jakartaMonkey = FixtureMonkeyFactory.giveMeJakartaMonkey().build()

            val request = jakartaMonkey.giveMeOne<SellerUserLoginRequest>()

            every {
                authenticateSellerUserUseCase.execute(any())
            } returns jakartaMonkey.giveMeOne<AuthenticateSellerUserQueryResult>()

            mockMvc.perform(
                put(SellerUserUrl.USER_SIGN_IN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            )
                .andExpectAll(
                    status().is2xxSuccessful,
                )
                .andDo(MockMvcResultHandlers.print())
                .andDo(
                    RestDocuments(
                        identifier = "signIn",
                        documentTags = listOf("seller_user"),
                        summary = "매장 회원 로그인",
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
