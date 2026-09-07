package com.reservation.rest.timetable.occupancy.confirm

import com.reservation.authenticate.port.input.ExtractIdentifierFromHeaderUseCase
import com.reservation.config.MockMvcFactory
import com.reservation.config.MockMvcFactory.objectMapper
import com.reservation.config.SpringRestDocsKotestExtension
import com.reservation.rest.timetable.request.ConfirmTimeTableOccupancyRequest
import com.reservation.timetable.exceptions.NoHoldToConfirmException
import com.reservation.timetable.port.input.ConfirmTimeTableOccupancyUseCase
import com.reservation.timetable.port.input.command.request.ConfirmTimeTableOccupancyCommand
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

class ConfirmTimeTableOccupancyControllerTest : FunSpec(
    {
        val restDocsExtension = SpringRestDocsKotestExtension()
        extension(restDocsExtension)

        lateinit var mockMvc: MockMvc
        lateinit var confirmTimeTableOccupancyUseCase: ConfirmTimeTableOccupancyUseCase
        lateinit var extractIdentifierFromHeaderUseCase: ExtractIdentifierFromHeaderUseCase

        beforeTest { testCase ->
            confirmTimeTableOccupancyUseCase = mockk<ConfirmTimeTableOccupancyUseCase>()
            extractIdentifierFromHeaderUseCase = mockk<ExtractIdentifierFromHeaderUseCase>()
            val controller =
                ConfirmTimeTableOccupancyController(
                    confirmTimeTableOccupancyUseCase,
                    extractIdentifierFromHeaderUseCase,
                )

            mockMvc =
                MockMvcFactory.buildMockMvc(
                    controller,
                    restDocsExtension.restDocumentation(testCase),
                )
        }

        test("올바른 파라미터가 주어지고 확정에 성공한다.") {
            val url = "/api/v1/time-table/booking/{restaurantId}/confirm"
            val restaurantId = UuidGenerator.generate()
            val request =
                ConfirmTimeTableOccupancyRequest(
                    date = LocalDate.now(),
                    startTime = LocalTime.of(11, 0),
                )

            every {
                extractIdentifierFromHeaderUseCase.execute(any())
            } returns UuidGenerator.generate()

            every {
                confirmTimeTableOccupancyUseCase.execute(any())
            } returns true

            mockMvc.perform(
                post(url, restaurantId)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(objectMapper.writeValueAsString(request)),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpectAll(
                    status().isOk,
                    jsonPath("$.result").value(true),
                )
        }

        test("date가 없는 파라미터가 주어지면 4xx로 거절된다.") {
            val url = "/api/v1/time-table/booking/{restaurantId}/confirm"
            val restaurantId = UuidGenerator.generate()
            val request = mapOf("startTime" to LocalTime.of(11, 0))

            every {
                extractIdentifierFromHeaderUseCase.execute(any())
            } returns UuidGenerator.generate()

            every {
                confirmTimeTableOccupancyUseCase.execute(any())
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

        test("startTime이 없는 파라미터가 주어지면 4xx로 거절된다.") {
            val url = "/api/v1/time-table/booking/{restaurantId}/confirm"
            val restaurantId = UuidGenerator.generate()
            val request = mapOf("date" to LocalDate.now())

            every {
                extractIdentifierFromHeaderUseCase.execute(any())
            } returns UuidGenerator.generate()

            every {
                confirmTimeTableOccupancyUseCase.execute(any())
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

        test("확정할 홀드가 없으면 400으로 거절된다.") {
            val url = "/api/v1/time-table/booking/{restaurantId}/confirm"
            val restaurantId = UuidGenerator.generate()
            val request =
                ConfirmTimeTableOccupancyRequest(
                    date = LocalDate.now(),
                    startTime = LocalTime.of(11, 0),
                )

            every {
                extractIdentifierFromHeaderUseCase.execute(any())
            } returns UuidGenerator.generate()

            every {
                confirmTimeTableOccupancyUseCase.execute(any())
            } throws NoHoldToConfirmException()

            mockMvc.perform(
                post(url, restaurantId)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(objectMapper.writeValueAsString(request)),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpectAll(
                    status().isBadRequest,
                )
        }

        // 클라이언트는 신원을 주입할 수 없다 — userId는 인증에서, restaurantId는 path에서 온다.
        test("커맨드에는 인증에서 얻은 userId와 path의 restaurantId, 본문의 슬롯만 실린다.") {
            val url = "/api/v1/time-table/booking/{restaurantId}/confirm"
            val restaurantId = UuidGenerator.generate()
            val userId = UuidGenerator.generate()
            val date = LocalDate.now()
            val startTime = LocalTime.of(11, 0)
            val commandSlot = slot<ConfirmTimeTableOccupancyCommand>()
            val request =
                ConfirmTimeTableOccupancyRequest(
                    date = date,
                    startTime = startTime,
                )

            every {
                extractIdentifierFromHeaderUseCase.execute(any())
            } returns userId

            every {
                confirmTimeTableOccupancyUseCase.execute(capture(commandSlot))
            } returns true

            mockMvc.perform(
                post(url, restaurantId)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(objectMapper.writeValueAsString(request)),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpectAll(
                    status().isOk,
                )

            commandSlot.captured shouldBe
                ConfirmTimeTableOccupancyCommand(
                    userId = userId,
                    restaurantId = restaurantId,
                    date = date,
                    startTime = startTime,
                )
        }
    },
)
