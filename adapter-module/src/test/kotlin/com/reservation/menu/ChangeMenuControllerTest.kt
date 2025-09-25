package com.reservation.menu

import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.config.MockMvcFactory
import com.reservation.config.MockMvcFactory.objectMapper
import com.reservation.config.SpringRestDocsKotestExtension
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
import org.mockito.ArgumentMatchers.any
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockPart
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.multipart
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
            mockMvc = MockMvcFactory.buildMockMvc(controller, restDocsExtension.restDocumentation)
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
        }
    }
}
