package com.reservation.restaurant

import com.fasterxml.jackson.databind.ObjectMapper
import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.ninjasquad.springmockk.MockkBean
import com.reservation.authenticate.port.input.ExtractIdentifierFromHeaderQuery
import com.reservation.config.security.TestSecurity
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.rest.restaurant.RestaurantUrl
import com.reservation.rest.restaurant.create.CreateRestaurantController
import com.reservation.rest.restaurant.request.CreateRestaurantRequest
import com.reservation.restaurant.port.input.CreateRestaurantCommand
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
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureRestDocs
@ActiveProfiles(value = ["test"])
@Import(value = [TestSecurity::class])
@WebMvcTest(CreateRestaurantController::class)
@ExtendWith(RestDocumentationExtension::class)
class CreateRestaurantControllerTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
) : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @MockkBean
    lateinit var createRestaurantCommand: CreateRestaurantCommand

    @MockkBean
    lateinit var extractIdentifierFromHeaderQuery: ExtractIdentifierFromHeaderQuery

    private val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

    private fun perfectCase() =
        pureMonkey.giveMeBuilder<CreateRestaurantRequest>()
            .setLazy("companyId") { Arbitraries.strings().ofLength(128).sample() }
            .setLazy("name") { Arbitraries.strings().ofLength(64).sample() }
            .setLazy("introduce") { Arbitraries.strings().ofLength(6000).sample() }
            .setLazy("phone") { Arbitraries.strings().ofMinLength(11).ofMaxLength(13).sample() }
            .setLazy("zipCode") { Arbitraries.strings().ofLength(5).sample() }
            .setLazy("address") { Arbitraries.strings().ofLength(256).sample() }
            .setLazy("detail") { Arbitraries.strings().ofLength(256).sample() }
            .setLazy("latitude") { Arbitraries.bigDecimals().ofScale(6).sample() }
            .setLazy("longitude") { Arbitraries.bigDecimals().ofScale(6).sample() }
            .sample()

    init {
        test("JakartaValidation - NotBlank인 케이스에 위배되어 실패한다.") {
            forAll(
                row("companyId"),
                row("name"),
                row("zipCode"),
                row("address"),
            ) { emptyField ->
                val request = perfectCase().copy(emptyField, "")

                mockMvc.perform(
                    post(RestaurantUrl.CREATE_RESTAURANT)
                        .header(
                            HttpHeaders.AUTHORIZATION,
                            CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                )
                    .andDo(MockMvcResultHandlers.print())
                    .andExpect(
                        status().is4xxClientError,
                    )
            }
        }
    }
}
