package com.reservation.user.general.sign

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.reservation.config.restdoc.Body
import com.reservation.config.restdoc.RestDocuments
import com.reservation.config.security.TestSecurity
import com.reservation.exceptions.UnauthorizedException
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.rest.user.general.GeneralUserUrl
import com.reservation.rest.user.general.sign.RefreshGeneralUserController
import com.reservation.rest.user.general.sign.RefreshTokenDefinitions
import com.reservation.user.self.port.input.RefreshAccessTokenQuery
import com.reservation.user.self.port.input.RefreshAccessTokenQuery.RefreshResult
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.mockk.every
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.payload.JsonFieldType.STRING
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureRestDocs
@ActiveProfiles(value = ["test"])
@Import(value = [TestSecurity::class])
@WebMvcTest(RefreshGeneralUserController::class)
@ExtendWith(RestDocumentationExtension::class)
class RefreshGeneralUserControllerTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
) : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @MockkBean
    private lateinit var refreshAccessTokenQuery: RefreshAccessTokenQuery

    init {

        test("리프레시에 실패한다.") {

            every {
                refreshAccessTokenQuery.refresh(any())
            } throws UnauthorizedException()

            mockMvc.perform(
                get(GeneralUserUrl.REFRESH)
                    .cookie(
                        Cookie(RefreshTokenDefinitions.REFRESH_TOKEN_KEY, "refresh token"),
                    ),
            )
                .andDo(print())
                .andExpectAll(
                    status().isUnauthorized,
                )
        }

        test("리프레시에 성공한다.") {

            every {
                refreshAccessTokenQuery.refresh(any())
            } returns
                RefreshResult(
                    CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                )

            mockMvc.perform(
                get(GeneralUserUrl.REFRESH)
                    .cookie(
                        Cookie(RefreshTokenDefinitions.REFRESH_TOKEN_KEY, "refresh token"),
                    ),
            )
                .andDo(print())
                .andExpectAll(
                    status().isOk,
                )
                .andDo(
                    RestDocuments(
                        identifier = "refresh",
                        documentTags = listOf("general_user"),
                        summary = "일반 사용자 액세스 토큰 리프레시",
                        responseBody =
                            arrayOf(
                                Body("accessToken", STRING, true, "액세스 토큰"),
                            ),
                    )
                        .create(),
                )
        }
    }
}
