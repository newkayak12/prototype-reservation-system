package com.reservation.menu

import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.config.MockMvcFactory
import com.reservation.config.MockMvcFactory.objectMapper
import com.reservation.config.SpringRestDocsKotestExtension
import com.reservation.config.restdoc.Body
import com.reservation.config.restdoc.Part
import com.reservation.config.restdoc.PathParameter
import com.reservation.config.restdoc.RequestPartFields
import com.reservation.config.restdoc.RestDocuments
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.menu.port.input.ChangeMenuUseCase
import com.reservation.rest.menu.MenuUrl
import com.reservation.rest.menu.change.ChangeMenuController
import com.reservation.rest.menu.request.ChangeMenuRequest
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import net.jqwik.api.Arbitraries
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockPart
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.multipart
import org.springframework.restdocs.payload.JsonFieldType.ARRAY
import org.springframework.restdocs.payload.JsonFieldType.BOOLEAN
import org.springframework.restdocs.payload.JsonFieldType.NUMBER
import org.springframework.restdocs.payload.JsonFieldType.STRING
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal

class ChangeMenuControllerTest : FunSpec() {
    init {
        val restDocsExtension = SpringRestDocsKotestExtension()
        extension(restDocsExtension)

        lateinit var changeMenuUseCase: ChangeMenuUseCase
        lateinit var mockMvc: MockMvc

        val jakartaMonkey = FixtureMonkeyFactory.giveMeJakartaMonkey().build()
        val perfectCase = jakartaMonkey.giveMeOne<ChangeMenuRequest>()
        val url = "${MenuUrl.PREFIX}/{id}"

        beforeTest { testCase ->
            changeMenuUseCase = mockk<ChangeMenuUseCase>()
            val controller = ChangeMenuController(changeMenuUseCase)
            mockMvc =
                MockMvcFactory.buildMockMvc(
                    controller,
                    restDocsExtension.restDocumentation(testCase),
                )
        }

        test("요청을 전달했지만 restaurantId가 없어 실패한다.") {
            val id = UuidGenerator.generate()
            val requestBody = perfectCase.copy(restaurantId = "")
            val requestPart =
                MockPart("request", objectMapper.writeValueAsBytes(requestBody))
                    .apply { headers.contentType = MediaType.APPLICATION_JSON }

            mockMvc.perform(
                multipart(url, id)
                    .part(requestPart)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .with {
                        it.method = "PUT"
                        it
                    }
                    .contentType(MediaType.MULTIPART_FORM_DATA),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is4xxClientError)
        }

        test("요청을 전달했지만 title이 공백이어서 실패한다.") {
            val id = UuidGenerator.generate()
            val requestBody = perfectCase.copy(title = "")
            val requestPart =
                MockPart("request", objectMapper.writeValueAsBytes(requestBody))
                    .apply { headers.contentType = MediaType.APPLICATION_JSON }

            mockMvc.perform(
                multipart(url, id)
                    .part(requestPart)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .with {
                        it.method = "PUT"
                        it
                    }
                    .contentType(MediaType.MULTIPART_FORM_DATA),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is4xxClientError)
        }

        test("요청을 전달했지만 title이 30자 초과여서  실패한다.") {
            val id = UuidGenerator.generate()
            val requestBody =
                perfectCase.copy(
                    title = Arbitraries.strings().ofMinLength(31).sample(),
                )
            val requestPart =
                MockPart("request", objectMapper.writeValueAsBytes(requestBody))
                    .apply { headers.contentType = MediaType.APPLICATION_JSON }

            mockMvc.perform(
                multipart(url, id)
                    .part(requestPart)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .with {
                        it.method = "PUT"
                        it
                    }
                    .contentType(MediaType.MULTIPART_FORM_DATA),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is4xxClientError)
        }

        test("요청을 전달했지만 description이 공백이어서 실패한다.") {
            val id = UuidGenerator.generate()
            val requestBody = perfectCase.copy(description = "")
            val requestPart =
                MockPart("request", objectMapper.writeValueAsBytes(requestBody))
                    .apply { headers.contentType = MediaType.APPLICATION_JSON }

            mockMvc.perform(
                multipart(url, id)
                    .part(requestPart)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .with {
                        it.method = "PUT"
                        it
                    }
                    .contentType(MediaType.MULTIPART_FORM_DATA),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is4xxClientError)
        }

        test("요청을 전달했지만 description이 255자 초과여서  실패한다.") {
            val id = UuidGenerator.generate()
            val requestBody =
                perfectCase.copy(
                    description = Arbitraries.strings().ofMinLength(256).sample(),
                )
            val requestPart =
                MockPart("request", objectMapper.writeValueAsBytes(requestBody))
                    .apply { headers.contentType = MediaType.APPLICATION_JSON }

            mockMvc.perform(
                multipart(url, id)
                    .part(requestPart)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .with {
                        it.method = "PUT"
                        it
                    }
                    .contentType(MediaType.MULTIPART_FORM_DATA),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is4xxClientError)
        }

        test("요청을 전달했지만 price가 음수여서 실패한다.") {
            val id = UuidGenerator.generate()
            val requestBody =
                perfectCase.copy(
                    price =
                        Arbitraries.bigDecimals().lessThan(BigDecimal.ZERO).sample(),
                )
            val requestPart =
                MockPart("request", objectMapper.writeValueAsBytes(requestBody))
                    .apply { headers.contentType = MediaType.APPLICATION_JSON }

            mockMvc.perform(
                multipart(url, id)
                    .part(requestPart)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .with {
                        it.method = "PUT"
                        it
                    }
                    .contentType(MediaType.MULTIPART_FORM_DATA),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is4xxClientError)
        }

        test("요청을 전달했지만 price가 999_999_999보다 커서 실패한다.") {
            val id = UuidGenerator.generate()
            val requestBody =
                perfectCase.copy(
                    price =
                        Arbitraries.bigDecimals()
                            .greaterThan(BigDecimal.valueOf(999_999_999L))
                            .sample(),
                )
            val requestPart =
                MockPart("request", objectMapper.writeValueAsBytes(requestBody))
                    .apply { headers.contentType = MediaType.APPLICATION_JSON }

            mockMvc.perform(
                multipart(url, id)
                    .part(requestPart)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .with {
                        it.method = "PUT"
                        it
                    }
                    .contentType(MediaType.MULTIPART_FORM_DATA),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is4xxClientError)
        }

        test("요청을 전달하고 JakartaValidation을 통과해서 성공한다.") {
            val id = UuidGenerator.generate()
            val requestBody = perfectCase
            val requestPart =
                MockPart("request", objectMapper.writeValueAsBytes(requestBody))
                    .apply { headers.contentType = MediaType.APPLICATION_JSON }

            every {
                changeMenuUseCase.execute(any())
            } returns true

            mockMvc.perform(
                multipart(url, id)
                    .part(requestPart)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    )
                    .with {
                        it.method = "PUT"
                        it
                    }
                    .contentType(MediaType.MULTIPART_FORM_DATA),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is2xxSuccessful)
                .andDo(
                    RestDocuments(
                        identifier = "changeMenu",
                        documentTags =
                            listOf(
                                "menu",
                                "change",
                            ),
                        summary = "메뉴 수정",
                        description = "사진과 함께 메뉴를 수정한다.",
                        pathParameter =
                            arrayOf(
                                PathParameter(
                                    name = "id",
                                    optional = false,
                                    description = "메뉴 id",
                                ),
                            ),
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
                                            Body(
                                                name = "photoUrl",
                                                jsonType = ARRAY,
                                                optional = true,
                                                description = "기존 이미지",
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
