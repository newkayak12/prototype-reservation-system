package com.reservation.rest.user.seller.sign

import com.reservation.config.restdoc.RestDocuments
import com.reservation.config.security.TestSecurity
import com.reservation.rest.user.RefreshTokenDefinitions
import com.reservation.rest.user.seller.SellerUserUrl
import com.reservation.rest.user.seller.sign.outcome.SellerUserSignOutController
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import jakarta.servlet.http.Cookie
import net.jqwik.api.Arbitraries
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Base64

@AutoConfigureRestDocs
@ActiveProfiles(value = ["test"])
@Import(value = [TestSecurity::class])
@WebMvcTest(SellerUserSignOutController::class)
@ExtendWith(RestDocumentationExtension::class)
class SellerUserSignOutControllerTest(
    private val mockMvc: MockMvc,
) : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    init {
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
                patch(SellerUserUrl.USER_SIGN_OUT)
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
    }
}
