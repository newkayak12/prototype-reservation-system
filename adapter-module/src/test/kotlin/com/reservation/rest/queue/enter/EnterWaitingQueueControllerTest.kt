package com.reservation.rest.queue.enter

import com.reservation.authenticate.port.input.ExtractIdentifierFromHeaderUseCase
import com.reservation.config.MockMvcFactory
import com.reservation.config.MockMvcFactory.objectMapper
import com.reservation.config.SpringRestDocsKotestExtension
import com.reservation.queue.port.input.EnterWaitingQueueUseCase
import com.reservation.queue.port.input.command.response.EnterWaitingQueueCommandResult
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
import java.time.LocalTime

class EnterWaitingQueueControllerTest : FunSpec(
    {
        val restDocsExtension = SpringRestDocsKotestExtension()
        extension(restDocsExtension)

        val url = "/api/v1/time-table/booking/{restaurantId}/queue"

        lateinit var mockMvc: MockMvc
        lateinit var enterWaitingQueueUseCase: EnterWaitingQueueUseCase
        lateinit var extractIdentifierFromHeaderUseCase: ExtractIdentifierFromHeaderUseCase

        beforeTest { testCase ->
            enterWaitingQueueUseCase = mockk<EnterWaitingQueueUseCase>()
            extractIdentifierFromHeaderUseCase = mockk<ExtractIdentifierFromHeaderUseCase>()
            val controller =
                EnterWaitingQueueController(
                    enterWaitingQueueUseCase,
                    extractIdentifierFromHeaderUseCase,
                )

            mockMvc =
                MockMvcFactory.buildMockMvc(
                    controller,
                    restDocsExtension.restDocumentation(testCase),
                )
        }

        test("올바른 파라미터가 주어지면 ticketId와 position을 응답한다.") {
            val restaurantId = UuidGenerator.generate()
            val ticketId = UuidGenerator.generate()
            val request =
                mapOf(
                    "date" to LocalDate.now(),
                    "startTime" to LocalTime.of(11, 0),
                )

            every {
                extractIdentifierFromHeaderUseCase.execute(any())
            } returns UuidGenerator.generate()

            every {
                enterWaitingQueueUseCase.execute(any())
            } returns EnterWaitingQueueCommandResult(ticketId = ticketId, position = 5)

            mockMvc.perform(
                post(url, restaurantId)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(objectMapper.writeValueAsString(request)),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpectAll(
                    status().isCreated,
                    jsonPath("$.ticketId").value(ticketId),
                    jsonPath("$.position").value(5),
                )
        }

        test("date가 없는 파라미터가 주어지면 4xx로 거절된다.") {
            val restaurantId = UuidGenerator.generate()
            val request = mapOf("startTime" to LocalTime.of(11, 0))

            every {
                extractIdentifierFromHeaderUseCase.execute(any())
            } returns UuidGenerator.generate()

            mockMvc.perform(
                post(url, restaurantId)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(objectMapper.writeValueAsString(request)),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpectAll(status().is4xxClientError)
        }

        test("startTime이 없는 파라미터가 주어지면 4xx로 거절된다.") {
            val restaurantId = UuidGenerator.generate()
            val request = mapOf("date" to LocalDate.now())

            every {
                extractIdentifierFromHeaderUseCase.execute(any())
            } returns UuidGenerator.generate()

            mockMvc.perform(
                post(url, restaurantId)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(objectMapper.writeValueAsString(request)),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpectAll(status().is4xxClientError)
        }
    },
)
