package com.reservation.rest.queue.inquiry

import com.reservation.config.MockMvcFactory
import com.reservation.config.SpringRestDocsKotestExtension
import com.reservation.enumeration.QueueStatus.ADMITTED
import com.reservation.enumeration.QueueStatus.EXPIRED
import com.reservation.enumeration.QueueStatus.WAITING
import com.reservation.queue.port.input.InquiryWaitingTicketUseCase
import com.reservation.queue.port.input.query.response.InquiryWaitingTicketQueryResult
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

class InquiryWaitingTicketControllerTest : FunSpec(
    {
        val restDocsExtension = SpringRestDocsKotestExtension()
        extension(restDocsExtension)

        val url = "/api/v1/time-table/booking/{restaurantId}/queue/{ticketId}"

        lateinit var mockMvc: MockMvc
        lateinit var inquiryWaitingTicketUseCase: InquiryWaitingTicketUseCase

        beforeTest { testCase ->
            inquiryWaitingTicketUseCase = mockk<InquiryWaitingTicketUseCase>()

            mockMvc =
                MockMvcFactory.buildMockMvc(
                    InquiryWaitingTicketController(inquiryWaitingTicketUseCase),
                    restDocsExtension.restDocumentation(testCase),
                )
        }

        test("대기 중인 티켓을 조회하면 WAITING과 순번을 응답한다.") {
            val restaurantId = UuidGenerator.generate()
            val ticketId = UuidGenerator.generate()

            every {
                inquiryWaitingTicketUseCase.execute(any())
            } returns
                InquiryWaitingTicketQueryResult(
                    ticketId = ticketId,
                    status = WAITING,
                    position = 12,
                )

            mockMvc.perform(
                get(url, restaurantId, ticketId)
                    .param("date", LocalDate.now().toString())
                    .param("startTime", "11:00:00"),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpectAll(
                    status().isOk,
                    jsonPath("$.ticketId").value(ticketId),
                    jsonPath("$.status").value(WAITING.name),
                    jsonPath("$.position").value(12),
                )
        }

        test("입장이 허용된 티켓을 조회하면 ADMITTED를 응답한다.") {
            val restaurantId = UuidGenerator.generate()
            val ticketId = UuidGenerator.generate()

            every {
                inquiryWaitingTicketUseCase.execute(any())
            } returns
                InquiryWaitingTicketQueryResult(
                    ticketId = ticketId,
                    status = ADMITTED,
                    position = null,
                )

            mockMvc.perform(
                get(url, restaurantId, ticketId)
                    .param("date", LocalDate.now().toString())
                    .param("startTime", "11:00:00"),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpectAll(
                    status().isOk,
                    jsonPath("$.status").value(ADMITTED.name),
                    jsonPath("$.position").doesNotExist(),
                )
        }

        test("어디에도 없는 티켓을 조회하면 EXPIRED를 응답한다.") {
            val restaurantId = UuidGenerator.generate()
            val ticketId = UuidGenerator.generate()

            every {
                inquiryWaitingTicketUseCase.execute(any())
            } returns
                InquiryWaitingTicketQueryResult(
                    ticketId = ticketId,
                    status = EXPIRED,
                    position = null,
                )

            mockMvc.perform(
                get(url, restaurantId, ticketId)
                    .param("date", LocalDate.now().toString())
                    .param("startTime", "11:00:00"),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpectAll(
                    status().isOk,
                    jsonPath("$.status").value(EXPIRED.name),
                )
        }
    },
)
