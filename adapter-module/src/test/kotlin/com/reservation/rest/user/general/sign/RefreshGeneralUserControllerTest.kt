package com.reservation.rest.user.general.sign

import com.reservation.config.MockMvcFactory
import com.reservation.config.SpringRestDocsKotestExtension
import com.reservation.config.restdoc.Body
import com.reservation.config.restdoc.RestDocuments
import com.reservation.exceptions.UnauthorizedException
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.rest.user.RefreshTokenDefinitions
import com.reservation.rest.user.general.GeneralUserUrl
import com.reservation.rest.user.general.sign.refresh.RefreshGeneralUserController
import com.reservation.user.self.port.input.RefreshGeneralUserAccessTokenUseCase
import com.reservation.user.self.port.input.query.response.RefreshResult
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.Cookie
import org.springframework.restdocs.payload.JsonFieldType.STRING
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class RefreshGeneralUserControllerTest : FunSpec(
    {
        val restDocsExtension = SpringRestDocsKotestExtension()
        extension(restDocsExtension)

        lateinit var mockMvc: MockMvc
        lateinit var refreshGeneralUserAccessTokenUseCase: RefreshGeneralUserAccessTokenUseCase

        beforeTest { testCase ->
            refreshGeneralUserAccessTokenUseCase = mockk<RefreshGeneralUserAccessTokenUseCase>()
            val controller = RefreshGeneralUserController(refreshGeneralUserAccessTokenUseCase)
            mockMvc =
                MockMvcFactory.buildMockMvc(
                    controller,
                    restDocsExtension.restDocumentation(testCase),
                )
        }

        test("리프레시에 실패한다.") {

            every {
                refreshGeneralUserAccessTokenUseCase.refresh(any())
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
                refreshGeneralUserAccessTokenUseCase.refresh(any())
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
    },
)
