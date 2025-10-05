package com.reservation.rest.schedule.holiday

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.reservation.config.MockMvcFactory
import com.reservation.config.SpringRestDocsKotestExtension
import com.reservation.config.restdoc.Body
import com.reservation.config.restdoc.PathParameter
import com.reservation.config.restdoc.RestDocuments
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.rest.schedule.holiday.controller.CreateHolidayController
import com.reservation.rest.schedule.holiday.request.CreateHolidayRequest
import com.reservation.schedule.port.input.CreateHolidayUseCase
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import net.jqwik.api.Arbitraries
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.JsonFieldType.BOOLEAN
import org.springframework.restdocs.payload.JsonFieldType.STRING
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

class CreateHolidayControllerTest : FunSpec(
    {
        val restDocsExtension = SpringRestDocsKotestExtension()
        extension(restDocsExtension)

        lateinit var mockMvc: MockMvc
        lateinit var createHolidayUseCase: CreateHolidayUseCase
        val url = HolidayUrl.CREATE

        beforeTest { testCase ->
            createHolidayUseCase = mockk<CreateHolidayUseCase>()
            val controller = CreateHolidayController(createHolidayUseCase)
            mockMvc =
                MockMvcFactory.buildMockMvc(
                    controller,
                    restDocsExtension.restDocumentation(testCase),
                )
        }

        val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
        val objectMapper =
            ObjectMapper()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        fun perfectCase() =
            pureMonkey.giveMeBuilder<CreateHolidayRequest>()
                .setLazy("date") {
                    LocalDate.of(
                        Arbitraries.integers().between(2024, 2030).sample(),
                        Arbitraries.integers().between(1, 12).sample(),
                        Arbitraries.integers().between(1, 28).sample(),
                    )
                }
                .sample()

        fun validRestaurantId() = UuidGenerator.generate()

        test("비정상 요청으로 날짜가 null인 경우 실패한다.") {
            val restaurantId = validRestaurantId()
            val requestBody = mapOf<String, Any?>("date" to null)

            every { createHolidayUseCase.execute(any()) } returns true

            mockMvc.perform(
                post(url, restaurantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    ),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is4xxClientError)
        }

        test("비정상 요청으로 잘못된 RestaurantId 형식인 경우 실패한다.") {
            val invalidRestaurantId = "invalid-restaurant-id"
            val requestBody = perfectCase()

            every { createHolidayUseCase.execute(any()) } returns true

            mockMvc.perform(
                post(url, invalidRestaurantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    ),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is4xxClientError)
        }

        test("비정상 요청으로 Authorization 헤더가 없는 경우 실패한다.") {
            val restaurantId = validRestaurantId()
            val requestBody = perfectCase()

            every { createHolidayUseCase.execute(any()) } returns true

            mockMvc.perform(
                post(url, restaurantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody)),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isCreated)
        }

        test("정상 요청으로 휴일 생성에 성공한다.") {
            val restaurantId = validRestaurantId()
            val requestBody = perfectCase()

            every { createHolidayUseCase.execute(any()) } returns true

            mockMvc.perform(
                post(url, restaurantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    ),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.result").isBoolean)
                .andExpect(jsonPath("$.result").value(true))
                .andDo(
                    RestDocuments(
                        identifier = "createHoliday",
                        documentTags = listOf("schedule", "holiday", "create"),
                        summary = "휴일 생성",
                        description = "레스토랑의 휴일을 생성합니다.",
                        requestBody =
                            arrayOf(
                                Body(
                                    name = "date",
                                    jsonType = STRING,
                                    optional = false,
                                    description = "휴일 날짜 (YYYY-MM-DD 형식)",
                                ),
                            ),
                        responseBody =
                            arrayOf(
                                Body("result", BOOLEAN, false, "생성 성공 여부"),
                            ),
                        pathParameter =
                            arrayOf(
                                PathParameter("id", false, "레스토랑 식별값"),
                            ),
                    )
                        .authorizedRequestHeader()
                        .create(),
                )
        }

        test("UseCase에서 false를 반환할 때 정상 응답하지만 결과는 false이다.") {
            val restaurantId = validRestaurantId()
            val requestBody = perfectCase()

            every { createHolidayUseCase.execute(any()) } returns false

            mockMvc.perform(
                post(url, restaurantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    ),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.result").isBoolean)
                .andExpect(jsonPath("$.result").value(false))
        }
    },
)

