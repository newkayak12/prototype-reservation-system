package com.reservation.queue.usecase

import com.reservation.queue.port.input.AdmitSlotUseCase
import com.reservation.queue.port.input.command.request.EnterWaitingQueueCommand
import com.reservation.queue.port.output.EnterWaitingQueue
import com.reservation.queue.port.output.EnterWaitingQueue.Companion.ADMITTED_POSITION
import com.reservation.queue.port.output.EnterWaitingQueue.EnterWaitingQueueInquiry
import com.reservation.queue.port.output.EnterWaitingQueue.EnteredTicket
import com.reservation.queue.port.output.IsTicketAdmitted
import com.reservation.queue.service.IssueWaitingTicketDomainService
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDate
import java.time.LocalTime

@ExtendWith(MockKExtension::class)
class EnterWaitingQueueServiceTest {
    companion object {
        /** 대기열이 확정해 돌려주는 티켓. 서비스가 만든 후보와 일부러 다른 값을 쓴다. */
        private const val ENTERED_TICKET_ID = "0123456789abcdef0123456789abcdef"
    }

    @MockK
    private lateinit var enterWaitingQueue: EnterWaitingQueue

    @MockK
    private lateinit var admitSlot: AdmitSlotUseCase

    @MockK
    private lateinit var isTicketAdmitted: IsTicketAdmitted

    private lateinit var enterWaitingQueueService: EnterWaitingQueueService

    private val issueWaitingTicketDomainService = IssueWaitingTicketDomainService()

    @BeforeEach
    fun init() {
        enterWaitingQueueService =
            EnterWaitingQueueService(
                issueWaitingTicketDomainService,
                enterWaitingQueue,
                admitSlot,
                isTicketAdmitted,
            )
    }

    /** 줄에 앞사람이 있어 이번 진입으로는 뽑히지 않는 상태. */
    private fun stayInQueue() {
        every { admitSlot.execute(any()) } returns 0
        every { isTicketAdmitted.query(any()) } returns false
    }

    private fun command(userId: String = UuidGenerator.generate()) =
        EnterWaitingQueueCommand(
            userId = userId,
            restaurantId = UuidGenerator.generate(),
            date = LocalDate.of(2026, 8, 26),
            startTime = LocalTime.of(11, 0),
        )

    @DisplayName("정상적인 대기열 진입 요청이 주어졌을 때")
    @Nested
    inner class `Request income to enter waiting queue` {
        @DisplayName("대기열 진입을 요청하면")
        @Nested
        inner class `When enter waiting queue` {
            @DisplayName("대기열이 확정한 ticketId와 position을 돌려준다")
            @Test
            fun `return ticket id and position`() {
                val target = command()
                val inquirySlot = slot<EnterWaitingQueueInquiry>()

                stayInQueue()
                every {
                    enterWaitingQueue.enter(capture(inquirySlot))
                } returns EnteredTicket(ENTERED_TICKET_ID, 7)

                val result = enterWaitingQueueService.execute(target)

                result.position shouldBe 7
                // 후보가 아니라 대기열이 확정해 준 값이 사용자에게 나가야 한다.
                result.ticketId shouldBe ENTERED_TICKET_ID
                inquirySlot.captured.userId shouldBe target.userId
                inquirySlot.captured.ticketId.isNotBlank() shouldBe true
                inquirySlot.captured.slot.restaurantId shouldBe target.restaurantId
                inquirySlot.captured.slot.date shouldBe target.date
                inquirySlot.captured.slot.startTime shouldBe target.startTime

                verify(exactly = 1) { enterWaitingQueue.enter(any()) }
            }
        }
    }

    @DisplayName("같은 사용자가 같은 슬롯에 두 번 진입할 때")
    @Nested
    inner class `Request income twice from same user` {
        @DisplayName("대기열 진입을 두 번 요청하면")
        @Nested
        inner class `When enter waiting queue twice` {
            // ticketId가 nonce가 되면서 후보는 매 요청마다 달라진다. 그럼에도 사용자에게
            // 같은 값이 나가는 이유는 서비스가 후보를 버리고 대기열이 확정한 티켓을 쓰기
            // 때문이다 — 멱등성의 근거가 "해시가 같아서"에서 "대기열이 골라줘서"로 옮겨갔다.
            @DisplayName("후보가 매번 달라도 대기열이 확정한 동일 ticketId를 돌려준다")
            @Test
            fun `return same ticket id`() {
                val userId = UuidGenerator.generate()
                val target = command(userId)
                val inquiries = mutableListOf<EnterWaitingQueueInquiry>()

                stayInQueue()
                every {
                    enterWaitingQueue.enter(capture(inquiries))
                } returns EnteredTicket(ENTERED_TICKET_ID, 1)

                val first = enterWaitingQueueService.execute(target)
                val second = enterWaitingQueueService.execute(target)

                first.ticketId shouldBe second.ticketId
                first.ticketId shouldBe ENTERED_TICKET_ID
                // 후보 자체는 서로 달랐다는 것까지 확인해야 이 테스트가 의미를 가진다.
                inquiries[0].ticketId shouldNotBe inquiries[1].ticketId
            }
        }
    }

    @DisplayName("이미 입장이 허용된 사용자가 다시 진입할 때")
    @Nested
    inner class `Request income from already admitted user` {
        @DisplayName("대기열 진입을 요청하면")
        @Nested
        inner class `When enter waiting queue again` {
            @DisplayName("대기열로 되돌리지 않고 ADMITTED 상태를 그대로 돌려준다")
            @Test
            fun `keep admitted state`() {
                val target = command()

                every {
                    enterWaitingQueue.enter(any())
                } returns EnteredTicket(ENTERED_TICKET_ID, ADMITTED_POSITION)

                val result = enterWaitingQueueService.execute(target)

                result.position shouldBe ADMITTED_POSITION
                result.ticketId shouldBe ENTERED_TICKET_ID
                // 이미 들어와 있는 사람이다. 큐를 밀 이유도, 승격 여부를 다시 물을 이유도 없다.
                verify(exactly = 0) { admitSlot.execute(any()) }
                verify(exactly = 0) { isTicketAdmitted.query(any()) }
            }
        }
    }

    @DisplayName("대기열이 비어 있고 입장 정원이 남아 있을 때")
    @Nested
    inner class `Queue is empty and capacity is available` {
        @DisplayName("대기열 진입을 요청하면")
        @Nested
        inner class `When enter waiting queue` {
            // 이게 없으면 아무도 줄을 서 있지 않고 정원이 통째로 놀고 있는데도 첫 사용자가
            // 폴링 한 주기를 기다린다. 붐비지 않는 시간대에 대기열이 순수한 지연이 된다.
            @DisplayName("줄을 세우지 않고 같은 응답에서 곧바로 ADMITTED를 돌려준다")
            @Test
            fun `admit immediately without waiting`() {
                val target = command()

                every {
                    enterWaitingQueue.enter(any())
                } returns EnteredTicket(ENTERED_TICKET_ID, 1)
                every { admitSlot.execute(any()) } returns 1
                every { isTicketAdmitted.query(any()) } returns true

                val result = enterWaitingQueueService.execute(target)

                result.position shouldBe ADMITTED_POSITION
                result.ticketId shouldBe ENTERED_TICKET_ID
                verify(exactly = 1) { admitSlot.execute(any()) }
            }
        }
    }

    @DisplayName("대기열에 앞사람이 있을 때")
    @Nested
    inner class `Someone is ahead in the queue` {
        @DisplayName("대기열 진입을 요청하면")
        @Nested
        inner class `When enter waiting queue` {
            // 진입도 큐를 밀지만, 미는 것과 내가 뽑히는 것은 별개다. 앞사람이 먼저 뽑히고
            // 나는 그대로 줄에 남아야 한다 - 여기서 새치기가 나면 FIFO가 깨진다.
            @DisplayName("큐를 밀되 자기 순번은 그대로 유지한다")
            @Test
            fun `push the queue but keep own position`() {
                val target = command()

                every {
                    enterWaitingQueue.enter(any())
                } returns EnteredTicket(ENTERED_TICKET_ID, 7)
                every { admitSlot.execute(any()) } returns 1
                every { isTicketAdmitted.query(any()) } returns false

                val result = enterWaitingQueueService.execute(target)

                result.position shouldBe 7
                verify(exactly = 1) { admitSlot.execute(any()) }
            }
        }
    }
}
