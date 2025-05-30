package com.reservation.user.general

import com.fasterxml.jackson.databind.ObjectMapper
import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.ninjasquad.springmockk.MockkBean
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.rest.user.general.GeneralUserSignInController
import com.reservation.rest.user.general.GeneralUserUrl
import com.reservation.rest.user.general.request.GeneralUserLoginRequest
import com.reservation.user.self.port.input.AuthenticateGeneralUserQuery
import io.kotest.core.spec.style.FunSpec
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.put

@WebMvcTest(GeneralUserSignInController::class)
@AutoConfigureRestDocs
class GeneralUserSignInControllerTest(
    val mockMvc: MockMvc,
    val objectMapper: ObjectMapper,
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

                mockMvc
                    .put(GeneralUserUrl.GENERAL_USER_LOGIN) {
                        contentType = MediaType.APPLICATION_JSON
                        content = objectMapper.writeValueAsString(request)
                    }
                    .andDo {
                        print()
                    }
                    .andExpectAll {
                        status { is4xxClientError() }
                    }
            }
        }
    }
}
