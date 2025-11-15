package com.reservation.rest.user.general.sign

import com.reservation.config.MockMvcFactory
import com.reservation.config.SpringRestDocsKotestExtension
import com.reservation.config.restdoc.RestDocuments
import com.reservation.rest.user.RefreshTokenDefinitions
import com.reservation.rest.user.general.GeneralUserUrl
import com.reservation.rest.user.general.sign.outcome.GeneralUserSignOutController
import io.kotest.core.spec.style.FunSpec
import jakarta.servlet.http.Cookie
import net.jqwik.api.Arbitraries
import org.springframework.http.HttpHeaders
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Base64

class GeneralUserSignOutControllerTest : FunSpec(
    {
        val restDocsExtension = SpringRestDocsKotestExtension()
        extension(restDocsExtension)

        lateinit var mockMvc: MockMvc

        beforeTest { testCase ->
            val controller = GeneralUserSignOutController()
            mockMvc =
                MockMvcFactory.buildMockMvc(
                    controller,
                    restDocsExtension.restDocumentation(testCase),
                )
        }

        val encoder = Base64.getEncoder()
        val accessToken =
            "Bearer ${
                encoder.encode(
                    Arbitraries.strings().ofMinLength(10).ofMaxLength(255).ascii().sample()
                        .toByteArray(),
                )
            }"

        val refreshToken =
            "Bearer ${
                encoder.encode(
                    Arbitraries.strings().ofMinLength(10).ofMaxLength(255).ascii().sample()
                        .toByteArray(),
                )
            }"
        val refreshCookie = Cookie(RefreshTokenDefinitions.REFRESH_TOKEN_KEY, refreshToken)
        refreshCookie.path = RefreshTokenDefinitions.REFRESH_TOKEN_PATH
        refreshCookie.secure = RefreshTokenDefinitions.SECURE
        refreshCookie.isHttpOnly = RefreshTokenDefinitions.HTTP_ONLY

        test("로그아웃을 시도함에 따라 refresh 토큰이 제거된다.") {
            mockMvc.perform(
                patch(GeneralUserUrl.USER_SIGN_OUT)
                    .header(HttpHeaders.AUTHORIZATION, accessToken)
                    .cookie(refreshCookie),
            )
                .andDo(print())
                .andExpectAll(
                    status().is2xxSuccessful,
                    content().string(""),
                    cookie().path(
                        RefreshTokenDefinitions.REFRESH_TOKEN_KEY,
                        RefreshTokenDefinitions.REFRESH_TOKEN_PATH,
                    ),
                    cookie().secure(
                        RefreshTokenDefinitions.REFRESH_TOKEN_KEY,
                        RefreshTokenDefinitions.SECURE,
                    ),
                    cookie().httpOnly(
                        RefreshTokenDefinitions.REFRESH_TOKEN_KEY,
                        RefreshTokenDefinitions.HTTP_ONLY,
                    ),
                    cookie().maxAge(
                        RefreshTokenDefinitions.REFRESH_TOKEN_KEY,
                        RefreshTokenDefinitions.FLUSH_AGE,
                    ),
                )
                .andDo(
                    RestDocuments(
                        identifier = "signOut",
                        documentTags = listOf("general_user"),
                        summary = "일반 회원 로그아웃",
                    )
                        .authorizedRequestHeader()
                        .create(),
                )
        }
    },
)
