package com.reservation.user.general.sign

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.reservation.config.restdoc.Body
import com.reservation.config.restdoc.RestDocuments
import com.reservation.config.security.TestSecurity
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.rest.user.general.GeneralUserUrl
import com.reservation.rest.user.general.request.GeneralUserSignUpRequest
import com.reservation.rest.user.general.sign.up.GeneralUserSignUpController
import com.reservation.user.self.port.input.CreateGeneralUserUseCase
import com.reservation.user.self.port.input.command.request.CreateGeneralUserCommand
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
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.JsonFieldType.BOOLEAN
import org.springframework.restdocs.payload.JsonFieldType.STRING
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureRestDocs
@ActiveProfiles(value = ["test"])
@Import(value = [TestSecurity::class])
@WebMvcTest(GeneralUserSignUpController::class)
@ExtendWith(RestDocumentationExtension::class)
class GeneralUserSignUpControllerTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
) : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @MockkBean
    private lateinit var createGeneralUserUseCase: CreateGeneralUserUseCase

    init {

        test("로그인 시도를 진행하며 해당 시도는 성공한다.") {
            val request =
                GeneralUserSignUpRequest(
                    loginId = CommonlyUsedArbitraries.loginIdArbitrary.sample(),
                    password = CommonlyUsedArbitraries.passwordArbitrary.sample(),
                    email = CommonlyUsedArbitraries.emailArbitrary.sample(),
                    mobile = CommonlyUsedArbitraries.phoneNumberArbitrary.sample(),
                    nickname = CommonlyUsedArbitraries.nicknameArbitrary.sample(),
                )

            every {
                createGeneralUserUseCase.execute(any<CreateGeneralUserCommand>())
            } returns true

            mockMvc.perform(
                post(GeneralUserUrl.USER_SIGN_UP)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(objectMapper.writeValueAsString(request)),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpectAll(
                    status().is2xxSuccessful,
                    status().isCreated,
                    jsonPath("$.result").isBoolean,
                    jsonPath("$.result").value(true),
                )
                .andDo(
                    RestDocuments(
                        identifier = "singUp",
                        documentTags = listOf("general_user"),
                        summary = "일반 사용자 회원 가입",
                        requestBody =
                            arrayOf(
                                Body("loginId", STRING, false, "사용자 아이디"),
                                Body("password", STRING, false, "비밀번호"),
                                Body("email", STRING, false, "이메일"),
                                Body("mobile", STRING, false, "전화번호"),
                                Body("nickname", STRING, false, "닉네임"),
                            ),
                        responseBody =
                            arrayOf(
                                Body("result", BOOLEAN, true, "결과"),
                            ),
                    )
                        .create(),
                )
        }

        test("NotEmpty에 걸려서 로그인 시도가 실패한다.") {

            forAll(
                row(
                    "",
                    CommonlyUsedArbitraries.passwordArbitrary.sample(),
                    CommonlyUsedArbitraries.emailArbitrary.sample(),
                    CommonlyUsedArbitraries.phoneNumberArbitrary.sample(),
                    CommonlyUsedArbitraries.nicknameArbitrary.sample(),
                ),
                row(
                    CommonlyUsedArbitraries.loginIdArbitrary.sample(),
                    "",
                    CommonlyUsedArbitraries.emailArbitrary.sample(),
                    CommonlyUsedArbitraries.phoneNumberArbitrary.sample(),
                    CommonlyUsedArbitraries.nicknameArbitrary.sample(),
                ),
                row(
                    CommonlyUsedArbitraries.loginIdArbitrary.sample(),
                    CommonlyUsedArbitraries.passwordArbitrary.sample(),
                    "",
                    CommonlyUsedArbitraries.phoneNumberArbitrary.sample(),
                    CommonlyUsedArbitraries.nicknameArbitrary.sample(),
                ),
                row(
                    CommonlyUsedArbitraries.loginIdArbitrary.sample(),
                    CommonlyUsedArbitraries.passwordArbitrary.sample(),
                    CommonlyUsedArbitraries.emailArbitrary.sample(),
                    "",
                    CommonlyUsedArbitraries.nicknameArbitrary.sample(),
                ),
                row(
                    CommonlyUsedArbitraries.loginIdArbitrary.sample(),
                    CommonlyUsedArbitraries.passwordArbitrary.sample(),
                    CommonlyUsedArbitraries.emailArbitrary.sample(),
                    CommonlyUsedArbitraries.phoneNumberArbitrary.sample(),
                    "",
                ),
            ) { id, password, email, phone, nickname ->
                val request = GeneralUserSignUpRequest(id, password, email, phone, nickname)

                mockMvc.perform(
                    post(GeneralUserUrl.USER_SIGN_UP)
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)),
                )
                    .andDo(MockMvcResultHandlers.print())
                    .andExpectAll(status().is4xxClientError)
            }
        }
    }
}
