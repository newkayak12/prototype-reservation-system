package com.reservation.queue.usecase

import com.reservation.queue.port.output.AdmitWaitingTickets
import com.reservation.queue.port.output.AdmitWaitingTickets.AdmitInquiry
import com.reservation.queue.vo.WaitingQueueSlot
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.matchers.shouldBe
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
class AdmitSlotServiceTest {
    companion object {
        private const val CAPACITY = 30
        private const val TIME_TO_LIVE_SECONDS = 300L
    }

    @MockK
    private lateinit var admitWaitingTickets: AdmitWaitingTickets

    private lateinit var admitSlotService: AdmitSlotService

    @BeforeEach
    fun init() {
        admitSlotService =
            AdmitSlotService(admitWaitingTickets, CAPACITY, TIME_TO_LIVE_SECONDS)
    }

    private fun queueSlot() =
        WaitingQueueSlot(
            restaurantId = UuidGenerator.generate(),
            date = LocalDate.of(2026, 8, 26),
            startTime = LocalTime.of(11, 0),
        )

    @DisplayName("슬롯 하나에 대한 승격 요청이 주어졌을 때")
    @Nested
    inner class `Admission for a single slot` {
        @DisplayName("승격을 시도하면")
        @Nested
        inner class `When admit` {
            @DisplayName("설정된 정원과 수명을 그대로 실어 보내고 승격 수를 돌려준다")
            @Test
            fun `pass configured capacity and time to live`() {
                val target = queueSlot()
                val inquirySlot = slot<AdmitInquiry>()

                every { admitWaitingTickets.admit(capture(inquirySlot)) } returns 5

                admitSlotService.execute(target) shouldBe 5

                inquirySlot.captured.slot shouldBe target
                inquirySlot.captured.capacity shouldBe CAPACITY
                inquirySlot.captured.admissionTimeToLive.seconds shouldBe TIME_TO_LIVE_SECONDS
                verify(exactly = 1) { admitWaitingTickets.admit(any()) }
            }
        }
    }
}
