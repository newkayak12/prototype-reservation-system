package com.reservation.rest.timetable.occupancy.create

import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.authenticate.port.input.ExtractIdentifierFromHeaderUseCase
import com.reservation.config.MockMvcFactory
import com.reservation.config.MockMvcFactory.objectMapper
import com.reservation.config.SpringRestDocsKotestExtension
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.rest.timetable.request.CreateTimeTableOccupancyRequest
import com.reservation.timetable.port.input.CreateTimeTableOccupancyUseCase
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

class CreateTimeTableOccupancyControllerTest : FunSpec(
    {
        val restDocsExtension = SpringRestDocsKotestExtension()
        extension(restDocsExtension)

        lateinit var mockMvc: MockMvc
        lateinit var createTimeTableOccupancyUseCase: CreateTimeTableOccupancyUseCase
        lateinit var extractIdentifierFromHeaderUseCase: ExtractIdentifierFromHeaderUseCase

        val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

        beforeTest { testCase ->
            createTimeTableOccupancyUseCase = mockk<CreateTimeTableOccupancyUseCase>()
            extractIdentifierFromHeaderUseCase = mockk<ExtractIdentifierFromHeaderUseCase>()
            val controller =
                CreateTimeTableOccupancyController(
                    createTimeTableOccupancyUseCase,
                    extractIdentifierFromHeaderUseCase,
                )

            mockMvc =
                MockMvcFactory.buildMockMvc(
                    controller,
                    restDocsExtension.restDocumentation(testCase),
                )
        }

        test("startTime이 없는 파라미터가 주어지고 예약에 성공한다.") {
            val url = "/api/v1/time-table/booking/{restaurantId}"
            val restaurantId = UuidGenerator.generate()
            val request = mapOf("date" to LocalDate.now())

            every {
                extractIdentifierFromHeaderUseCase.execute(any())
            } returns UuidGenerator.generate()

            every {
                createTimeTableOccupancyUseCase.execute(any())
            } returns true

            mockMvc.perform(
                post(url, restaurantId)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(objectMapper.writeValueAsString(request)),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpectAll(
                    status().is4xxClientError,
                )
        }

        test("date가 없는 파라미터가 주어지고 예약에 성공한다.") {
            val url = "/api/v1/time-table/booking/{restaurantId}"
            val restaurantId = UuidGenerator.generate()
            val request = mapOf("startTime" to LocalDate.now())

            every {
                extractIdentifierFromHeaderUseCase.execute(any())
            } returns UuidGenerator.generate()

            every {
                createTimeTableOccupancyUseCase.execute(any())
            } returns true

            mockMvc.perform(
                post(url, restaurantId)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(objectMapper.writeValueAsString(request)),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpectAll(
                    status().is4xxClientError,
                )
        }

        test("올바른 파라미터가 주어지고 예약에 성공한다.") {
            val url = "/api/v1/time-table/booking/{restaurantId}"
            val restaurantId = UuidGenerator.generate()
            val request = pureMonkey.giveMeOne<CreateTimeTableOccupancyRequest>()

            every {
                extractIdentifierFromHeaderUseCase.execute(any())
            } returns UuidGenerator.generate()

            every {
                createTimeTableOccupancyUseCase.execute(any())
            } returns true

            mockMvc.perform(
                post(url, restaurantId)
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
        }
    },
)
