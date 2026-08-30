package com.reservation.rest.timetable.occupancy.create

import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.authenticate.port.input.ExtractIdentifierFromHeaderUseCase
import com.reservation.config.MockMvcFactory
import com.reservation.config.MockMvcFactory.objectMapper
import com.reservation.config.SpringRestDocsKotestExtension
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.queue.exceptions.QueueNotAdmittedException
import com.reservation.rest.timetable.request.CreateTimeTableOccupancyRequest
import com.reservation.timetable.port.input.CreateTimeTableOccupancyUseCase
import com.reservation.timetable.port.input.command.request.CreateTimeTableOccupancyCommand
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.LocalTime

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

        test("대기열에서 ADMITTED 되지 않은 ticketId로 호출하면 4xx로 거절된다.") {
            val url = "/api/v1/time-table/booking/{restaurantId}"
            val restaurantId = UuidGenerator.generate()
            val request = pureMonkey.giveMeOne<CreateTimeTableOccupancyRequest>()

            every {
                extractIdentifierFromHeaderUseCase.execute(any())
            } returns UuidGenerator.generate()

            every {
                createTimeTableOccupancyUseCase.execute(any())
            } throws QueueNotAdmittedException()

            mockMvc.perform(
                post(url, restaurantId)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(objectMapper.writeValueAsString(request)),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpectAll(
                    status().is4xxClientError,
                    status().isBadRequest,
                )
        }

        // 대기열 티켓은 더 이상 요청 본문에서 받지 않는다.
        // 게이트가 userId + 슬롯으로 서버에서 파생하므로, ticketId 없이도 요청이 성립해야 하고
        // 커맨드에는 인증에서 얻은 userId와 슬롯만 실려야 한다.
        test("ticketId 없이도 예약 요청이 성립하고 커맨드에는 userId와 슬롯만 실린다.") {
            val url = "/api/v1/time-table/booking/{restaurantId}"
            val restaurantId = UuidGenerator.generate()
            val userId = UuidGenerator.generate()
            val date = LocalDate.now()
            val startTime = LocalTime.of(11, 0)
            val commandSlot = slot<CreateTimeTableOccupancyCommand>()
            val request = mapOf("date" to date, "startTime" to startTime)

            every {
                extractIdentifierFromHeaderUseCase.execute(any())
            } returns userId

            every {
                createTimeTableOccupancyUseCase.execute(capture(commandSlot))
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
                )

            commandSlot.captured shouldBe
                CreateTimeTableOccupancyCommand(
                    userId = userId,
                    restaurantId = restaurantId,
                    date = date,
                    startTime = startTime,
                )
        }
    },
)
