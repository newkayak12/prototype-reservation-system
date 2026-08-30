package com.reservation.queue.usecase

import com.reservation.queue.port.input.AdmitSlotUseCase
import com.reservation.queue.port.output.LoadWaitingQueueSlots
import com.reservation.queue.vo.WaitingQueueSlot
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDate
import java.time.LocalTime

@ExtendWith(MockKExtension::class)
class AdmitWaitingQueueServiceTest {
    @MockK
    private lateinit var loadWaitingQueueSlots: LoadWaitingQueueSlots

    @MockK
    private lateinit var admitSlot: AdmitSlotUseCase

    private lateinit var admitWaitingQueueService: AdmitWaitingQueueService

    @BeforeEach
    fun init() {
        admitWaitingQueueService =
            AdmitWaitingQueueService(loadWaitingQueueSlots, admitSlot)
    }

    private fun queueSlot() =
        WaitingQueueSlot(
            restaurantId = UuidGenerator.generate(),
            date = LocalDate.of(2026, 8, 26),
            startTime = LocalTime.of(11, 0),
        )

    @DisplayName("대기열이 살아있는 슬롯이 여러 개 있을 때")
    @Nested
    inner class `Multiple slots are alive` {
        @DisplayName("리컨실 워커가 한 사이클을 돌면")
        @Nested
        inner class `When worker runs once` {
            @DisplayName("모든 슬롯에 대해 승격을 시도하고 총합을 돌려준다")
            @Test
            fun `admit every slot and sum up`() {
                val slots = listOf(queueSlot(), queueSlot())

                every { loadWaitingQueueSlots.query() } returns slots
                every { admitSlot.execute(slots[0]) } returns 1
                every { admitSlot.execute(slots[1]) } returns 2

                admitWaitingQueueService.execute() shouldBe 3

                verify(exactly = 1) { loadWaitingQueueSlots.query() }
                verify(exactly = 1) { admitSlot.execute(slots[0]) }
                verify(exactly = 1) { admitSlot.execute(slots[1]) }
            }
        }
    }

    @DisplayName("살아있는 대기열 슬롯이 하나도 없을 때")
    @Nested
    inner class `No slot is alive` {
        @DisplayName("리컨실 워커가 한 사이클을 돌면")
        @Nested
        inner class `When worker runs once` {
            @DisplayName("아무것도 승격시키지 않는다")
            @Test
            fun `admit nothing`() {
                every { loadWaitingQueueSlots.query() } returns emptyList()

                admitWaitingQueueService.execute() shouldBe 0

                verify(exactly = 0) { admitSlot.execute(any()) }
            }
        }
    }
}
