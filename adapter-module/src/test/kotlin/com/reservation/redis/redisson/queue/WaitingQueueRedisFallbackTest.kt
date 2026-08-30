package com.reservation.redis.redisson.queue

import com.reservation.queue.port.output.AdmitWaitingTickets.AdmitInquiry
import com.reservation.queue.port.output.EnterWaitingQueue.EnterWaitingQueueInquiry
import com.reservation.queue.port.output.EnterWaitingQueue.EnteredTicket
import com.reservation.queue.port.output.FindWaitingTicketPosition.WaitingTicketInquiry
import com.reservation.queue.port.output.IsTicketAdmitted.AdmissionInquiry
import com.reservation.queue.port.output.WaitingQueueFallbackCoordinator
import com.reservation.queue.vo.WaitingQueueSlot
import com.reservation.redis.redisson.queue.adapter.AdmitWaitingTicketsRedisAdapter
import com.reservation.redis.redisson.queue.adapter.EnterWaitingQueueRedisAdapter
import com.reservation.redis.redisson.queue.adapter.FindWaitingTicketPositionRedisAdapter
import com.reservation.redis.redisson.queue.adapter.IsTicketAdmittedRedisAdapter
import com.reservation.redis.redisson.queue.adapter.LoadWaitingQueueSlotsRedisAdapter
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.redisson.api.RBucket
import org.redisson.api.RPermitExpirableSemaphore
import org.redisson.api.RScoredSortedSet
import org.redisson.api.RSetCache
import org.redisson.api.RedissonClient
import org.redisson.client.RedisException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * Redis 장애 시 DB(`waiting_queue`) 폴백 경로 검증.
 *
 * `DistributedLockAspectTest`가 Redis 락 어댑터를 mock으로 세워 두고 아스펙트의 분기만 검증하듯,
 * 여기서도 `RedissonClient`를 mock으로 세워 `RedisException`을 던지게 하고
 * 각 포트 구현체가 `WaitingQueueFallbackCoordinator`로 갈아타는지만 검증한다.
 */
class WaitingQueueRedisFallbackTest {
    private val redissonClient = mockk<RedissonClient>()
    private val fallbackCoordinator = mockk<WaitingQueueFallbackCoordinator>()

    private val slot =
        WaitingQueueSlot(
            restaurantId = UuidGenerator.generate(),
            date = LocalDate.of(2026, 8, 26),
            startTime = LocalTime.of(11, 0),
        )
    private val ticketId = UuidGenerator.generate()
    private val userId = UuidGenerator.generate()
    private val ticketTimeToLive = 1_800L

    @BeforeEach
    fun init() {
        clearAllMocks()
    }

    private fun downScoredSortedSet() {
        every {
            redissonClient.getScoredSortedSet<String>(any<String>())
        } throws RedisException("Unable to connect to Redis")
    }

    /** 진입 경로는 `TICKET_OF` 버킷을 먼저 건드리므로 여기도 같이 죽어 있어야 현실적이다. */
    private fun downBucket() {
        every {
            redissonClient.getBucket<String>(any<String>())
        } throws RedisException("Unable to connect to Redis")
    }

    private fun downSetCache() {
        every {
            redissonClient.getSetCache<String>(any<String>())
        } throws RedisException("Unable to connect to Redis")
    }

    @DisplayName("Redis가 죽었을 때 대기열 진입은")
    @Nested
    inner class `When redis is down on enter` {
        @DisplayName("DB 폴백으로 순번을 발급받는다")
        @Test
        fun `fall back to database`() {
            downBucket()
            downScoredSortedSet()
            every {
                fallbackCoordinator.enter(slot, userId, ticketId)
            } returns EnteredTicket(ticketId, 42)

            val adapter =
                EnterWaitingQueueRedisAdapter(
                    redissonClient,
                    fallbackCoordinator,
                    ticketTimeToLive,
                )
            val entered =
                adapter.enter(
                    EnterWaitingQueueInquiry(
                        slot = slot,
                        userId = userId,
                        ticketId = ticketId,
                    ),
                )

            entered.position shouldBe 42
            entered.ticketId shouldBe ticketId
            verify(exactly = 1) { fallbackCoordinator.enter(slot, userId, ticketId) }
        }
    }

    @DisplayName("Redis가 죽었을 때 순번 조회는")
    @Nested
    inner class `When redis is down on find position` {
        @DisplayName("DB 폴백으로 순번을 조회한다")
        @Test
        fun `fall back to database`() {
            downScoredSortedSet()
            every { fallbackCoordinator.findPosition(slot, ticketId) } returns 7

            val adapter =
                FindWaitingTicketPositionRedisAdapter(redissonClient, fallbackCoordinator)
            val position =
                adapter.query(WaitingTicketInquiry(slot = slot, ticketId = ticketId))

            position shouldBe 7
            verify(exactly = 1) { fallbackCoordinator.findPosition(slot, ticketId) }
        }
    }

    @DisplayName("Redis가 죽었을 때 입장 허용 여부 확인은")
    @Nested
    inner class `When redis is down on admission check` {
        @DisplayName("DB 폴백으로 확인한다")
        @Test
        fun `fall back to database`() {
            downSetCache()
            every { fallbackCoordinator.isAdmitted(slot, ticketId) } returns true

            val adapter = IsTicketAdmittedRedisAdapter(redissonClient, fallbackCoordinator)

            adapter.query(AdmissionInquiry(slot = slot, ticketId = ticketId)) shouldBe true
            verify(exactly = 1) { fallbackCoordinator.isAdmitted(slot, ticketId) }
        }
    }

    @DisplayName("Redis가 죽었을 때 슬롯 목록 조회는")
    @Nested
    inner class `When redis is down on load slots` {
        @DisplayName("DB 폴백으로 조회한다")
        @Test
        fun `fall back to database`() {
            downSetCache()
            every { fallbackCoordinator.loadSlots() } returns listOf(slot)

            val adapter =
                LoadWaitingQueueSlotsRedisAdapter(redissonClient, fallbackCoordinator)

            adapter.query() shouldBe listOf(slot)
            verify(exactly = 1) { fallbackCoordinator.loadSlots() }
        }
    }

    @DisplayName("Redis가 죽었을 때 입장 허용 승격은")
    @Nested
    inner class `When redis is down on admit` {
        @DisplayName("permit pool을 건드리지 않고 DB 폴백으로 승격한다")
        @Test
        fun `fall back to database`() {
            val timeToLive = Duration.ofMinutes(5)
            val capacity = 10

            downScoredSortedSet()
            every { fallbackCoordinator.admit(slot, capacity, timeToLive) } returns 3

            val adapter = AdmitWaitingTicketsRedisAdapter(redissonClient, fallbackCoordinator)
            val admitted =
                adapter.admit(
                    AdmitInquiry(
                        slot = slot,
                        capacity = capacity,
                        admissionTimeToLive = timeToLive,
                    ),
                )

            admitted shouldBe 3
            verify(exactly = 1) { fallbackCoordinator.admit(slot, capacity, timeToLive) }
            verify(exactly = 0) {
                redissonClient.getPermitExpirableSemaphore(any<String>())
            }
        }
    }

    /**
     * 사이클 도중에 Redis가 죽는 경우.
     *
     * permit 획득을 `AcquireSemaphoreTemplate`에 위임하면 그쪽의
     * `runCatching {}.getOrElse { false }`가 `RedisException`을 `false`로 바꿔 삼켜버려
     * "permit이 없다"로 오인되고 DB 폴백이 **아예 발동하지 않는다**.
     * 예외가 그대로 올라와 폴백으로 이어져야 한다.
     */
    @DisplayName("사이클 도중에 Redis가 죽었을 때")
    @Nested
    inner class `When redis dies in the middle of a cycle` {
        @DisplayName("permit 획득 실패로 오인하지 않고 DB 폴백으로 넘어간다")
        @Test
        fun `fall back to database instead of swallowing`() {
            val timeToLive = Duration.ofMinutes(5)
            val capacity = 10
            val queue = mockk<RScoredSortedSet<String>>()
            val semaphore = mockk<RPermitExpirableSemaphore>()

            every { redissonClient.getScoredSortedSet<String>(any<String>()) } returns queue
            every { queue.isEmpty } returns false
            every { redissonClient.getPermitExpirableSemaphore(any<String>()) } returns semaphore
            every { semaphore.trySetPermits(any()) } returns true
            every { semaphore.expire(any<Duration>()) } returns true
            every {
                semaphore.tryAcquire(any<Long>(), any<Long>(), any())
            } throws RedisException("Unable to connect to Redis")
            every { fallbackCoordinator.admit(slot, capacity, timeToLive) } returns 3

            val adapter = AdmitWaitingTicketsRedisAdapter(redissonClient, fallbackCoordinator)

            adapter.admit(
                AdmitInquiry(
                    slot = slot,
                    capacity = capacity,
                    admissionTimeToLive = timeToLive,
                ),
            ) shouldBe 3
            verify(exactly = 1) { fallbackCoordinator.admit(slot, capacity, timeToLive) }
        }
    }

    @DisplayName("Redis가 살아있을 때는")
    @Nested
    inner class `When redis is alive` {
        @DisplayName("DB 폴백을 전혀 호출하지 않는다")
        @Test
        fun `never touch database fallback`() {
            val queue = mockk<RScoredSortedSet<String>>()
            val admittedSet = mockk<RSetCache<String>>()
            val semaphore = mockk<RPermitExpirableSemaphore>()
            val permitBucket = mockk<RBucket<String>>()
            val permitId = UuidGenerator.generate()

            every { redissonClient.getScoredSortedSet<String>(any<String>()) } returns queue
            every { redissonClient.getSetCache<String>(any<String>()) } returns admittedSet
            every { redissonClient.getPermitExpirableSemaphore(any<String>()) } returns semaphore
            // 승격과 함께 permitId를 PERMIT_OF에 보관한다. 이게 없으면 permit을 되돌려 줄
            // 방법이 없어 예약을 마친 사용자의 입장 자리가 lease 만료까지 묶인다.
            every { redissonClient.getBucket<String>(any<String>()) } returns permitBucket
            every { permitBucket.set(any<String>(), any<Duration>()) } returns Unit
            every { queue.isEmpty } returns false
            every { queue.pollFirst() } returns ticketId andThen null
            every { admittedSet.add(any<String>(), any(), any()) } returns true
            every { semaphore.trySetPermits(any()) } returns true
            every { semaphore.expire(any<Duration>()) } returns true
            every {
                semaphore.tryAcquire(any<Long>(), any<Long>(), any())
            } returns permitId andThen permitId andThen null
            every { semaphore.tryRelease(permitId) } returns true

            val adapter = AdmitWaitingTicketsRedisAdapter(redissonClient, fallbackCoordinator)
            val admitted =
                adapter.admit(
                    AdmitInquiry(
                        slot = slot,
                        capacity = 10,
                        admissionTimeToLive = Duration.ofMinutes(5),
                    ),
                )

            // 두 번째 pollFirst가 비어 있으면 이미 얻은 permit을 반드시 돌려줘야 한다.
            admitted shouldBe 1
            verify(exactly = 1) { semaphore.tryRelease(permitId) }
            // 승격된 티켓에 대해서만 permitId를 보관한다 (돌려준 permit 몫은 저장하지 않는다).
            verify(exactly = 1) { permitBucket.set(permitId, any<Duration>()) }
            verify(exactly = 0) {
                fallbackCoordinator.admit(any(), any(), any())
            }
        }
    }
}
