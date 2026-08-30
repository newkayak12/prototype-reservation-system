package com.reservation.persistence.queue

import com.reservation.persistence.queue.entity.WaitingQueueEntity
import com.reservation.persistence.queue.repository.adapter.WaitingQueueDatabaseAdapter
import com.reservation.persistence.queue.repository.jpa.WaitingQueueJpaRepository
import com.reservation.persistence.queue.repository.jpa.projection.WaitingQueueSlotProjection
import com.reservation.queue.port.output.EnterWaitingQueue.Companion.ADMITTED_POSITION
import com.reservation.queue.vo.WaitingQueueSlot
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Pageable
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Redis 장애 시 실제로 계산을 수행하는 DB 좌표계의 산술을 검증한다.
 * (`WaitingQueueRedisFallbackTest`가 "폴백으로 넘어가는가"를 본다면 여기는 "넘어간 뒤 맞게 계산하는가"를 본다.)
 */
class WaitingQueueDatabaseAdapterTest {
    private val waitingQueueJpaRepository = mockk<WaitingQueueJpaRepository>()
    private val adapter = WaitingQueueDatabaseAdapter(waitingQueueJpaRepository)

    private val slot =
        WaitingQueueSlot(
            restaurantId = UuidGenerator.generate(),
            date = LocalDate.of(2026, 8, 26),
            startTime = LocalTime.of(11, 0),
        )
    private val ticketId = UuidGenerator.generate()
    private val userId = UuidGenerator.generate()
    private val timeToLive: Duration = Duration.ofMinutes(5)

    /**
     * 실제로는 DB의 auto-increment가 채워주는 값이므로, 영속화된 상태를 흉내내기 위해
     * 테스트에서만 id를 직접 주입한다.
     */
    private fun entity(id: Long = 1) =
        WaitingQueueEntity(
            restaurantId = slot.restaurantId,
            date = slot.date,
            startTime = slot.startTime,
            userId = userId,
            ticketId = ticketId,
        ).also {
            WaitingQueueEntity::class.java.getDeclaredField("id")
                .apply { isAccessible = true }
                .set(it, id)
        }

    private fun stubFind(entity: WaitingQueueEntity?) {
        every {
            waitingQueueJpaRepository.findByRestaurantIdAndDateAndStartTimeAndTicketId(
                slot.restaurantId,
                slot.date,
                slot.startTime,
                ticketId,
            )
        } returns entity
        stubFindByUser(entity)
    }

    /**
     * 진입의 멱등 기준이 티켓이 아니라 사용자이므로, `enter`는 사용자 조회를 탄다.
     * (ticketId가 nonce라 요청마다 값이 달라 티켓으로는 같은 사람을 못 알아본다.)
     */
    private fun stubFindByUser(entity: WaitingQueueEntity?) {
        every {
            waitingQueueJpaRepository.findByRestaurantIdAndDateAndStartTimeAndUserId(
                slot.restaurantId,
                slot.date,
                slot.startTime,
                userId,
            )
        } returns entity
    }

    @DisplayName("아직 대기열에 없는 티켓이 주어졌을 때")
    @Nested
    inner class `Ticket is not in queue yet` {
        @DisplayName("대기열 진입을 요청하면")
        @Nested
        inner class `When enter` {
            @DisplayName("row를 새로 만들고 순번을 계산한다")
            @Test
            fun `insert row and calculate position`() {
                val saved = entity()

                stubFind(null)
                every { waitingQueueJpaRepository.save(any<WaitingQueueEntity>()) } returns saved
                every {
                    waitingQueueJpaRepository.countPreceding(any(), any(), any(), any())
                } returns 4

                adapter.enter(slot, userId, ticketId).position shouldBe 4

                verify(exactly = 1) { waitingQueueJpaRepository.save(any<WaitingQueueEntity>()) }
            }
        }
    }

    @DisplayName("이미 대기열에 있는 티켓이 주어졌을 때")
    @Nested
    inner class `Ticket is already in queue` {
        @DisplayName("대기열 진입을 다시 요청하면")
        @Nested
        inner class `When enter again` {
            @DisplayName("row를 새로 만들지 않는다")
            @Test
            fun `do not insert row again`() {
                stubFind(entity())
                every {
                    waitingQueueJpaRepository.countPreceding(any(), any(), any(), any())
                } returns 2

                adapter.enter(slot, userId, ticketId).position shouldBe 2

                verify(exactly = 0) { waitingQueueJpaRepository.save(any<WaitingQueueEntity>()) }
            }
        }
    }

    @DisplayName("이미 입장이 허용된 티켓이 주어졌을 때")
    @Nested
    inner class `Ticket is already admitted` {
        @DisplayName("상태를 조회하면")
        @Nested
        inner class `When query` {
            @DisplayName("ADMITTED로 판정되고 대기 순번은 없다")
            @Test
            fun `admitted and no position`() {
                val admitted = entity().apply { admit(LocalDateTime.now()) }

                stubFind(admitted)

                adapter.isAdmitted(slot, ticketId) shouldBe true
                adapter.findPosition(slot, ticketId) shouldBe null
            }
        }

        @DisplayName("대기열 진입을 다시 요청하면")
        @Nested
        inner class `When enter again` {
            @DisplayName("대기열로 되돌리지 않고 ADMITTED 순번을 돌려준다")
            @Test
            fun `do not requeue admitted ticket`() {
                stubFind(entity().apply { admit(LocalDateTime.now()) })

                adapter.enter(slot, userId, ticketId).position shouldBe ADMITTED_POSITION

                verify(exactly = 0) {
                    waitingQueueJpaRepository.save(any<WaitingQueueEntity>())
                    waitingQueueJpaRepository.countPreceding(any(), any(), any(), any())
                }
            }
        }
    }

    @DisplayName("대기열에 아예 없는 티켓이 주어졌을 때")
    @Nested
    inner class `Ticket does not exist` {
        @DisplayName("상태를 조회하면")
        @Nested
        inner class `When query` {
            @DisplayName("ADMITTED도 아니고 순번도 없다")
            @Test
            fun `neither admitted nor positioned`() {
                stubFind(null)

                adapter.isAdmitted(slot, ticketId) shouldBe false
                adapter.findPosition(slot, ticketId) shouldBe null
            }
        }
    }

    @DisplayName("이미 일부가 입장 허용된 슬롯이 주어졌을 때")
    @Nested
    inner class `Slot already has admitted tickets` {
        @DisplayName("입장 허용을 요청하면")
        @Nested
        inner class `When admit` {
            @DisplayName("후보를 먼저 잠근 뒤 남은 자리 수만큼만 승격시킨다")
            @Test
            fun `lock candidates first then admit only vacancy`() {
                val capacity = 10
                val alreadyAdmitted = 7L
                val vacancy = capacity - alreadyAdmitted.toInt()
                val candidates = (1..capacity).map { entity(it.toLong()) }
                val pageableSlot = slot<Pageable>()
                val savedSlot = slot<List<WaitingQueueEntity>>()

                every {
                    waitingQueueJpaRepository.countAdmitted(any(), any(), any(), any())
                } returns alreadyAdmitted
                every {
                    waitingQueueJpaRepository.findWaitingForUpdate(
                        any(),
                        any(),
                        any(),
                        capture(pageableSlot),
                    )
                } returns candidates
                every {
                    waitingQueueJpaRepository.saveAll(capture(savedSlot))
                } answers { savedSlot.captured }

                adapter.admit(slot, capacity, timeToLive) shouldBe vacancy

                // 잠그는 범위는 "이번 사이클 최대치"인 capacity, 실제 승격은 남은 자리만큼.
                pageableSlot.captured.pageSize shouldBe capacity
                savedSlot.captured.size shouldBe vacancy
                savedSlot.captured.all { it.admittedAt != null } shouldBe true

                // 정원을 채운 후보는 손대지 않는다.
                candidates.drop(vacancy).all { it.admittedAt == null } shouldBe true
            }

            @DisplayName("잠금 조회가 COUNT보다 먼저 수행된다")
            @Test
            fun `lock before count`() {
                val capacity = 10

                every {
                    waitingQueueJpaRepository.findWaitingForUpdate(any(), any(), any(), any())
                } returns listOf(entity())
                every {
                    waitingQueueJpaRepository.countAdmitted(any(), any(), any(), any())
                } returns 0
                every { waitingQueueJpaRepository.saveAll(any<List<WaitingQueueEntity>>()) } returns
                    emptyList()

                adapter.admit(slot, capacity, timeToLive)

                verifyOrder {
                    waitingQueueJpaRepository.findWaitingForUpdate(any(), any(), any(), any())
                    waitingQueueJpaRepository.countAdmitted(any(), any(), any(), any())
                }
            }
        }
    }

    @DisplayName("이미 허용치를 가득 채운 슬롯이 주어졌을 때")
    @Nested
    inner class `Slot capacity is exhausted` {
        @DisplayName("입장 허용을 요청하면")
        @Nested
        inner class `When admit` {
            @DisplayName("아무도 승격시키지 않고 저장도 하지 않는다")
            @Test
            fun `admit nobody`() {
                every {
                    waitingQueueJpaRepository.findWaitingForUpdate(any(), any(), any(), any())
                } returns listOf(entity())
                every {
                    waitingQueueJpaRepository.countAdmitted(any(), any(), any(), any())
                } returns 10

                adapter.admit(slot, 10, timeToLive) shouldBe 0

                verify(exactly = 0) {
                    waitingQueueJpaRepository.saveAll(any<List<WaitingQueueEntity>>())
                }
            }
        }
    }

    @DisplayName("대기 중인 슬롯이 존재할 때")
    @Nested
    inner class `Waiting slots exist` {
        @DisplayName("슬롯 목록을 조회하면")
        @Nested
        inner class `When load slots` {
            @DisplayName("도메인 슬롯으로 변환해 돌려준다")
            @Test
            fun `map to domain slot`() {
                val projection = mockk<WaitingQueueSlotProjection>()

                every { projection.getRestaurantId() } returns slot.restaurantId
                every { projection.getDate() } returns slot.date
                every { projection.getStartTime() } returns slot.startTime
                every { waitingQueueJpaRepository.findWaitingSlots() } returns listOf(projection)

                adapter.loadSlots() shouldBe listOf(slot)
            }
        }
    }
}
