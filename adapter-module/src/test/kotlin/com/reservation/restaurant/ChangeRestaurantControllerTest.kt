package com.reservation.restaurant

import com.fasterxml.jackson.databind.ObjectMapper
import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.ninjasquad.springmockk.MockkBean
import com.reservation.authenticate.port.input.ExtractIdentifierFromHeaderQuery
import com.reservation.config.security.TestSecurity
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.rest.restaurant.RestaurantUrl
import com.reservation.rest.restaurant.change.ChangeRestaurantController
import com.reservation.rest.restaurant.request.CreateRestaurantRequest
import com.reservation.restaurant.port.input.UpdateRestaurantCommand
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.core.spec.style.FunSpec
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.extensions.spring.SpringExtension
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
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureRestDocs
@ActiveProfiles(value = ["test"])
@Import(value = [TestSecurity::class])
@WebMvcTest(ChangeRestaurantController::class)
@ExtendWith(RestDocumentationExtension::class)
class ChangeRestaurantControllerTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
) : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @MockkBean
    private lateinit var updateRestaurantCommand: UpdateRestaurantCommand

    @MockkBean
    private lateinit var extractIdentifierFromHeaderQuery: ExtractIdentifierFromHeaderQuery

    private val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

    private fun perfectCase() =
        pureMonkey.giveMeBuilder<CreateRestaurantRequest>()
            .setLazy("name") { Arbitraries.strings().numeric().ofLength(64).sample() }
            .setLazy("introduce") { Arbitraries.strings().ofLength(6000).sample() }
            .setLazy(
                "phone",
            ) { Arbitraries.strings().numeric().ofMinLength(11).ofMaxLength(13).sample() }
            .setLazy("zipCode") { Arbitraries.strings().numeric().ofLength(5).sample() }
            .setLazy("address") { Arbitraries.strings().numeric().ofLength(256).sample() }
            .setLazy("detail") { Arbitraries.strings().ofLength(256).sample() }
            .setLazy("latitude") { Arbitraries.bigDecimals().ofScale(6).sample() }
            .setLazy("longitude") { Arbitraries.bigDecimals().ofScale(6).sample() }
            .sample()

    init {
        test("비정상 요청으로 (JakartaValidation - NotBlank)인 케이스에 위배되어 실패한다.") {
            forAll(
                row("name"),
                row("zipCode"),
                row("address"),
            ) { emptyField ->
                val requestBody = perfectCase().copy(emptyField, "")

                val request =
                    MockPart("request", objectMapper.writeValueAsBytes(requestBody))
                        .apply { headers.contentType = MediaType.APPLICATION_JSON }

                mockMvc.perform(
                    multipart("${RestaurantUrl.PREFIX}/{id}", UuidGenerator.generate())
                        .part(request)
                        .header(
                            HttpHeaders.AUTHORIZATION,
                            CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                        )
                        .contentType(MediaType.MULTIPART_FORM_DATA),
                )
                    .andDo(MockMvcResultHandlers.print())
                    .andExpect(
                        status().is4xxClientError,
                    )
            }
        }

        test("비정상 요청으로 (JakartaValidation - Size)에 위배되어 실패한다.") {
            forAll(
                row("phone", 11, 13),
                row("address", 1, 256),
            ) { fieldName: String, min: Int, max: Int ->

                val value =
                    Arbitraries.oneOf(
                        Arbitraries.strings().numeric().ofMinLength(max + 1),
                        Arbitraries.strings().numeric().ofMaxLength(min - 1),
                    )
                        .sample()

                val requestBody =
                    when (fieldName) {
                        "phone" -> perfectCase().copy(phone = value)
                        "address" -> perfectCase().copy(address = value)
                        else -> perfectCase()
                    }

                val request =
                    MockPart("request", objectMapper.writeValueAsBytes(requestBody))
                        .apply { headers.contentType = MediaType.APPLICATION_JSON }

                mockMvc.perform(
                    multipart("${RestaurantUrl.PREFIX}/{id}", UuidGenerator.generate())
                        .part(request)
                        .header(
                            HttpHeaders.AUTHORIZATION,
                            CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                        )
                        .contentType(MediaType.MULTIPART_FORM_DATA),
                )
                    .andDo(MockMvcResultHandlers.print())
                    .andExpect(
                        status().is4xxClientError,
                    )
            }
        }

        test("비정상 요청으로 (JakartaValidation - Nullable이고 최대 Size)만 있는 경우 위배되어 실패한다.") {
            forAll(
                row("introduce", 6000),
                row("detail", 256),
            ) { fieldName: String, max: Int ->

                val requestBody =
                    when (fieldName) {
                        "introduce" ->
                            perfectCase().copy(
                                introduce = Arbitraries.strings().ofMinLength(max + 1).sample(),
                            )
                        "detail" ->
                            perfectCase().copy(
                                detail = Arbitraries.strings().ofMinLength(max + 1).sample(),
                            )
                        else -> perfectCase()
                    }

                val request =
                    MockPart("request", objectMapper.writeValueAsBytes(requestBody))
                        .apply { headers.contentType = MediaType.APPLICATION_JSON }

                mockMvc.perform(
                    multipart("${RestaurantUrl.PREFIX}/{id}", UuidGenerator.generate())
                        .part(request)
                        .header(
                            HttpHeaders.AUTHORIZATION,
                            CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                        )
                        .contentType(MediaType.MULTIPART_FORM_DATA),
                )
                    .andDo(MockMvcResultHandlers.print())
                    .andExpect(
                        status().is4xxClientError,
                    )
            }
        }
    }
}
