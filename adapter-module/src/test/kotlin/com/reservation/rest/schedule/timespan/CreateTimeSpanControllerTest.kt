package com.reservation.rest.schedule.timespan

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
import com.reservation.rest.schedule.timespan.controller.CreateTimeSpanController
import com.reservation.rest.schedule.timespan.request.CreateTimeSpanRequest
import com.reservation.schedule.exceptions.InvalidTimeSpanElementException
import com.reservation.schedule.port.input.CreateTimeSpanUseCase
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
import java.time.LocalTime

/**
 * CreateTimeSpanController 테스트 시나리오
 */
class CreateTimeSpanControllerTest : FunSpec(
    {
        val restDocsExtension = SpringRestDocsKotestExtension()
        extension(restDocsExtension)

        lateinit var mockMvc: MockMvc
        lateinit var createTimeSpanUseCase: CreateTimeSpanUseCase
        val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
        var url = TimeSpanUrl.CREATE
        val objectMapper =
            ObjectMapper()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        beforeTest { testCase ->
            createTimeSpanUseCase = mockk<CreateTimeSpanUseCase>()
            val controller = CreateTimeSpanController(createTimeSpanUseCase)
            mockMvc =
                MockMvcFactory.buildMockMvc(
                    controller,
                    restDocsExtension.restDocumentation(testCase),
                )
        }

        fun perfectCase(): CreateTimeSpanRequest {
            val now = LocalTime.of(9, 0)
            return pureMonkey.giveMeBuilder<CreateTimeSpanRequest>()
                .set("startTime", now)
                .set("endTime", now.plusHours(5))
                .sample()
        }

        fun validRestaurantId() = UuidGenerator.generate()

        // ## 비정상 요청 케이스
        // - test("비정상 요청으로 startTime이 endTime보다 늦은 경우 실패한다.")
        test("Parameter 중 startTime, endTime의 유효성이 잘못되어 생성에 실패한다.") {

            val restaurantId = validRestaurantId()
            val requestParameter = perfectCase()

            every {
                createTimeSpanUseCase.execute(any())
            } throws InvalidTimeSpanElementException(Arbitraries.strings().sample())

            mockMvc.perform(
                post(url, restaurantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestParameter))
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    ),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is4xxClientError)
        }

        // - test("비정상 요청으로 잘못된 RestaurantId 형식인 경우 실패한다.")
        test("RestaurantId의 형식이 잘못되어 실패한다.") {

            val restaurantId = Arbitraries.strings().sample()
            val requestParameter = perfectCase()

            mockMvc.perform(
                post(url, restaurantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestParameter))
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        CommonlyUsedArbitraries.bearerTokenArbitrary.sample(),
                    ),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().is4xxClientError)
                .andExpect(status().isNotFound)
        }

        // ## 정상 요청 케이스
        // - test("정상 요청으로 시간표 생성에 성공한다.")
        test("정상 요청으로 시간표 생성에 성공한다.") {

            val restaurantId = validRestaurantId()
            val requestParameter = perfectCase()

            every {
                createTimeSpanUseCase.execute(any())
            } returns true

            mockMvc.perform(
                post(url, restaurantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestParameter))
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
                        identifier = "createTimeSpan",
                        documentTags = listOf("schedule", "timespan", "create"),
                        summary = "시간 생성",
                        description = "레스토랑의 시간을 생성합니다.",
                        requestBody =
                            arrayOf(
                                Body(
                                    name = "day",
                                    jsonType = STRING,
                                    optional = false,
                                    description = "요일",
                                ),
                                Body(
                                    name = "startTime",
                                    jsonType = STRING,
                                    optional = false,
                                    description = "시작 시간",
                                ),
                                Body(
                                    name = "endTime",
                                    jsonType = STRING,
                                    optional = false,
                                    description = "종료 시간",
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
    },
)
