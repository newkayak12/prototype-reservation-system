package com.reservation.menu

import com.fasterxml.jackson.databind.ObjectMapper
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.ninjasquad.springmockk.MockkBean
import com.reservation.config.restdoc.Body
import com.reservation.config.restdoc.Part
import com.reservation.config.restdoc.RequestPartFields
import com.reservation.config.restdoc.RestDocuments
import com.reservation.config.security.TestSecurity
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.menu.port.input.CreateMenuUseCase
import com.reservation.rest.menu.MenuUrl
import com.reservation.rest.menu.create.CreateMenuController
import com.reservation.rest.menu.request.CreateMenuRequest
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.mockk.every
import net.jqwik.api.Arbitraries
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockPart
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.multipart
import org.springframework.restdocs.payload.JsonFieldType.BOOLEAN
import org.springframework.restdocs.payload.JsonFieldType.NUMBER
import org.springframework.restdocs.payload.JsonFieldType.STRING
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal

@AutoConfigureRestDocs
@ActiveProfiles(value = ["test"])
@Import(value = [TestSecurity::class])
@WebMvcTest(CreateMenuController::class)
@ExtendWith(RestDocumentationExtension::class)
class CreateMenuControllerTest(
    private val objectMapper: ObjectMapper,
    private val mockMvc: MockMvc,
) : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @MockkBean
    private lateinit var createMenuUseCase: CreateMenuUseCase

    private val jakartaMonkey = FixtureMonkeyFactory.giveMeJakartaMonkey().build()

    private fun perfectCase() = jakartaMonkey.giveMeOne<CreateMenuRequest>()

    init {
        test("올바르지 않은 파라미터(restaurantId - empty)로 등록 요청을 하고 등록에 실패를 한다.") {
            val requestBody = perfectCase().copy(restaurantId = "")
            val request =
                MockPart("request", objectMapper.writeValueAsBytes(requestBody))
                    .apply { headers.contentType = MediaType.APPLICATION_JSON }

            mockMvc.perform(
                multipart(MenuUrl.CREATE)
                    .part(request)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .contentType(MediaType.MULTIPART_FORM_DATA),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is4xxClientError)
        }

        test("올바르지 않은 파라미터(title - empty)로 등록 요청을 하고 등록에 실패를 한다.") {
            val requestBody = perfectCase().copy(title = "")
            val request =
                MockPart("request", objectMapper.writeValueAsBytes(requestBody))
                    .apply { headers.contentType = MediaType.APPLICATION_JSON }

            mockMvc.perform(
                multipart(MenuUrl.CREATE)
                    .part(request)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .contentType(MediaType.MULTIPART_FORM_DATA),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is4xxClientError)
        }

        test("올바르지 않은 파라미터(title: length > 30)로 등록 요청을 하고 등록에 실패를 한다.") {
            val requestBody =
                perfectCase().copy(title = Arbitraries.strings().ofMinLength(31).sample())
            val request =
                MockPart("request", objectMapper.writeValueAsBytes(requestBody))
                    .apply { headers.contentType = MediaType.APPLICATION_JSON }

            mockMvc.perform(
                multipart(MenuUrl.CREATE)
                    .part(request)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .contentType(MediaType.MULTIPART_FORM_DATA),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is4xxClientError)
        }

        test("올바르지 않은 파라미터(description - empty)로 등록 요청을 하고 등록에 실패를 한다.") {
            val requestBody = perfectCase().copy(description = "")
            val request =
                MockPart("request", objectMapper.writeValueAsBytes(requestBody))
                    .apply { headers.contentType = MediaType.APPLICATION_JSON }

            mockMvc.perform(
                multipart(MenuUrl.CREATE)
                    .part(request)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .contentType(MediaType.MULTIPART_FORM_DATA),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is4xxClientError)
        }

        test("올바르지 않은 파라미터(description: length > 255)로 등록 요청을 하고 등록에 실패를 한다.") {
            val requestBody =
                perfectCase().copy(description = Arbitraries.strings().ofMinLength(256).sample())
            val request =
                MockPart("request", objectMapper.writeValueAsBytes(requestBody))
                    .apply { headers.contentType = MediaType.APPLICATION_JSON }

            mockMvc.perform(
                multipart(MenuUrl.CREATE)
                    .part(request)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .contentType(MediaType.MULTIPART_FORM_DATA),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is4xxClientError)
        }

        test("올바르지 않은 파라미터(price: value < 0)로 등록 요청을 하고 등록에 실패를 한다.") {
            val requestBody =
                perfectCase().copy(
                    price =
                        Arbitraries
                            .bigDecimals()
                            .lessThan(BigDecimal.ZERO)
                            .sample(),
                )
            val request =
                MockPart("request", objectMapper.writeValueAsBytes(requestBody))
                    .apply { headers.contentType = MediaType.APPLICATION_JSON }

            mockMvc.perform(
                multipart(MenuUrl.CREATE)
                    .part(request)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .contentType(MediaType.MULTIPART_FORM_DATA),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is4xxClientError)
        }

        test("올바르지 않은 파라미터(price: value > 999,999,999)로 등록 요청을 하고 등록에 실패를 한다.") {
            val requestBody =
                perfectCase().copy(
                    price =
                        Arbitraries
                            .bigDecimals()
                            .greaterThan(BigDecimal.valueOf(999_999_999L))
                            .sample(),
                )
            val request =
                MockPart("request", objectMapper.writeValueAsBytes(requestBody))
                    .apply { headers.contentType = MediaType.APPLICATION_JSON }

            mockMvc.perform(
                multipart(MenuUrl.CREATE)
                    .part(request)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .contentType(MediaType.MULTIPART_FORM_DATA),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is4xxClientError)
        }

        test("올바른 파라미터와 사진 없이 등록 요청을 하고 등록에 성공한다.") {
            val requestBody = perfectCase()
            val request =
                MockPart("request", objectMapper.writeValueAsBytes(requestBody))
                    .apply { headers.contentType = MediaType.APPLICATION_JSON }

            every {
                createMenuUseCase.execute(any())
            } returns true

            mockMvc.perform(
                multipart(MenuUrl.CREATE)
                    .part(request)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .contentType(MediaType.MULTIPART_FORM_DATA),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is2xxSuccessful)
                .andExpect(jsonPath("$.result").isBoolean)
                .andExpect(jsonPath("$.result").value(true))
        }

        test("올바른 파라미터와 사진을 등록 요청을 하고 등록에 성공한다.") {
            val requestBody = perfectCase()
            val request =
                MockPart("request", objectMapper.writeValueAsBytes(requestBody))
                    .apply { headers.contentType = MediaType.APPLICATION_JSON }
            val photo1 =
                MockPart("photos", "img1.jpg", byteArrayOf(1, 2, 3))
                    .apply { headers.contentType = MediaType.IMAGE_JPEG }
            val photo2 =
                MockPart("photos", "img2.jpg", byteArrayOf(4, 5, 6))
                    .apply { headers.contentType = MediaType.IMAGE_JPEG }
            every {
                createMenuUseCase.execute(any())
            } returns true

            mockMvc.perform(
                multipart(MenuUrl.CREATE)
                    .part(request)
                    .part(photo1)
                    .part(photo2)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .contentType(MediaType.MULTIPART_FORM_DATA),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is2xxSuccessful)
                .andExpect(jsonPath("$.result").isBoolean)
                .andExpect(jsonPath("$.result").value(true))
                .andDo(
                    RestDocuments(
                        identifier = "createMenu",
                        documentTags =
                            listOf(
                                "menu",
                                "create",
                            ),
                        summary = "메뉴 등록",
                        description = "사진과 함께 메뉴를 등록한다.",
                        requestPart =
                            arrayOf(
                                Part("request", false, "JSON 요청"),
                                Part("photos", true, "이미지 파일"),
                            ),
                        requestPartBody =
                            arrayOf(
                                RequestPartFields(
                                    partName = "request",
                                    jsonFields =
                                        arrayOf(
                                            Body(
                                                name = "restaurantId",
                                                jsonType = STRING,
                                                optional = false,
                                                description = "음식점 식별자",
                                            ),
                                            Body(
                                                name = "title",
                                                jsonType = STRING,
                                                optional = false,
                                                description = "메뉴 이름",
                                            ),
                                            Body(
                                                name = "description",
                                                jsonType = STRING,
                                                optional = false,
                                                description = "메뉴 설명",
                                            ),
                                            Body(
                                                name = "price",
                                                jsonType = NUMBER,
                                                optional = false,
                                                description = "가격",
                                            ),
                                            Body(
                                                name = "isRepresentative",
                                                jsonType = BOOLEAN,
                                                optional = true,
                                                description = "대표 여부",
                                            ),
                                            Body(
                                                name = "isRecommended",
                                                jsonType = BOOLEAN,
                                                optional = true,
                                                description = "추천 여부",
                                            ),
                                            Body(
                                                name = "isVisible",
                                                jsonType = BOOLEAN,
                                                optional = true,
                                                description = "노출 여부",
                                            ),
                                        ),
                                ),
                            ),
                        responseBody =
                            arrayOf(
                                Body("result", BOOLEAN, false, "결과"),
                            ),
                    )
                        .authorizedRequestHeader()
                        .create(),
                )
        }
    }
}
