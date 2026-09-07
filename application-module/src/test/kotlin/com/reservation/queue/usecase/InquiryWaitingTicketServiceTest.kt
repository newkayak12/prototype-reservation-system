package com.reservation.queue.usecase

import com.reservation.enumeration.QueueStatus.ADMITTED
import com.reservation.enumeration.QueueStatus.CONFIRMED
import com.reservation.enumeration.QueueStatus.EXPIRED
import com.reservation.enumeration.QueueStatus.WAITING
import com.reservation.queue.port.input.AdmitSlotUseCase
import com.reservation.queue.port.input.query.request.InquiryWaitingTicketQuery
import com.reservation.queue.port.output.FindTicketResult
import com.reservation.queue.port.output.FindWaitingTicketPosition
import com.reservation.queue.port.output.IsTicketAdmitted
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDate
import java.time.LocalTime

@ExtendWith(MockKExtension::class)
class InquiryWaitingTicketServiceTest {
    @MockK
    private lateinit var findTicketResult: FindTicketResult

    @MockK
    private lateinit var isTicketAdmitted: IsTicketAdmitted

    @MockK
    private lateinit var findWaitingTicketPosition: FindWaitingTicketPosition

    @MockK
    private lateinit var admitSlot: AdmitSlotUseCase

    @InjectMockKs
    private lateinit var inquiryWaitingTicketService: InquiryWaitingTicketService

    /**
     * 폴링은 이제 조회이면서 동시에 큐를 미는 행위다. 아래 시나리오들은 "민 결과"가 아니라
     * "판정 결과"를 보는 것이므로, 미는 동작은 여기서 기본값으로 깔아 둔다.
     */
    @BeforeEach
    fun init() {
        every { admitSlot.execute(any()) } returns 0
    }

    private fun query() =
        InquiryWaitingTicketQuery(
            restaurantId = UuidGenerator.generate(),
            date = LocalDate.of(2026, 8, 26),
            startTime = LocalTime.of(11, 0),
            ticketId = UuidGenerator.generate(),
        )

    @DisplayName("대기열에 남아있는 티켓이 주어졌을 때")
    @Nested
    inner class `Ticket is still waiting` {
        @DisplayName("상태를 조회하면")
        @Nested
        inner class `When inquiry ticket` {
            @DisplayName("WAITING과 순번을 돌려준다")
            @Test
            fun `return waiting with position`() {
                val target = query()

                every { findTicketResult.query(any()) } returns null
                every { isTicketAdmitted.query(any()) } returns false
                every { findWaitingTicketPosition.query(any()) } returns 12

                val result = inquiryWaitingTicketService.execute(target)

                result.status shouldBe WAITING
                result.position shouldBe 12
                result.ticketId shouldBe target.ticketId
            }
        }
    }

    @DisplayName("입장이 허용된 티켓이 주어졌을 때")
    @Nested
    inner class `Ticket is admitted` {
        @DisplayName("상태를 조회하면")
        @Nested
        inner class `When inquiry ticket` {
            @DisplayName("ADMITTED를 돌려주고 순번은 조회하지 않는다")
            @Test
            fun `return admitted without position`() {
                val target = query()

                every { findTicketResult.query(any()) } returns null
                every { isTicketAdmitted.query(any()) } returns true

                val result = inquiryWaitingTicketService.execute(target)

                result.status shouldBe ADMITTED
                result.position shouldBe null
                verify(exactly = 0) { findWaitingTicketPosition.query(any()) }
            }
        }
    }

    @DisplayName("Phase 4가 RESULT 키에 최종 결과를 기록한 티켓이 주어졌을 때")
    @Nested
    inner class `Ticket already has result` {
        @DisplayName("상태를 조회하면")
        @Nested
        inner class `When inquiry ticket` {
            @DisplayName("RESULT 값을 그대로 최종 상태로 돌려준다")
            @Test
            fun `return result status`() {
                val target = query()

                every { findTicketResult.query(target.ticketId) } returns CONFIRMED

                val result = inquiryWaitingTicketService.execute(target)

                result.status shouldBe CONFIRMED
                verify(exactly = 0) {
                    isTicketAdmitted.query(any())
                    findWaitingTicketPosition.query(any())
                }
            }
        }
    }

    @DisplayName("대기열에도 ADMITTED에도 RESULT에도 없는 티켓이 주어졌을 때")
    @Nested
    inner class `Ticket disappeared everywhere` {
        @DisplayName("상태를 조회하면")
        @Nested
        inner class `When inquiry ticket` {
            @DisplayName("EXPIRED를 돌려준다")
            @Test
            fun `return expired`() {
                val target = query()

                every { findTicketResult.query(any()) } returns null
                every { isTicketAdmitted.query(any()) } returns false
                every { findWaitingTicketPosition.query(any()) } returns null

                val result = inquiryWaitingTicketService.execute(target)

                result.status shouldBe EXPIRED
                result.position shouldBe null
            }
        }
    }

    @DisplayName("대기 중인 사용자가 순번을 확인할 때")
    @Nested
    inner class `Waiting user polls for position` {
        @DisplayName("상태를 조회하면")
        @Nested
        inner class `When inquiry ticket` {
            // 승격을 타이머에만 맡기면 주기가 곧 처리량 상한이 된다. 기다리는 사람은 반드시
            // 폴링 중이므로, 폴링 자체를 큐를 미는 계기로 쓴다.
            @DisplayName("조회하면서 자기 슬롯의 대기열을 민다")
            @Test
            fun `push the queue while polling`() {
                val target = query()

                every { findTicketResult.query(any()) } returns null
                every { isTicketAdmitted.query(any()) } returns false
                every { findWaitingTicketPosition.query(any()) } returns 12

                inquiryWaitingTicketService.execute(target)

                verify(exactly = 1) {
                    admitSlot.execute(
                        match {
                            it.restaurantId == target.restaurantId &&
                                it.date == target.date &&
                                it.startTime == target.startTime
                        },
                    )
                }
            }

            // 순서가 뒤집히면 이번 호출로 뽑힌 사용자가 자기 승격을 다음 폴링에서야 알게 되어
            // 폴링 한 주기를 그대로 손해 본다.
            @DisplayName("상태를 판정하기 전에 큐를 밀어, 이번 호출로 뽑혔으면 같은 응답에 실린다")
            @Test
            fun `admit before judging status`() {
                val target = query()

                every { findTicketResult.query(any()) } returns null
                every { isTicketAdmitted.query(any()) } returns true

                val result = inquiryWaitingTicketService.execute(target)

                result.status shouldBe ADMITTED
                verifyOrder {
                    admitSlot.execute(any())
                    isTicketAdmitted.query(any())
                }
            }
        }
    }
}
