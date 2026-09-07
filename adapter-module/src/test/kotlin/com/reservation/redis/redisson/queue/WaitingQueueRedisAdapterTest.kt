package com.reservation.redis.redisson.queue

import com.reservation.queue.port.output.AdmitWaitingTickets.AdmitInquiry
import com.reservation.queue.port.output.EnterWaitingQueue.Companion.ADMITTED_POSITION
import com.reservation.queue.port.output.EnterWaitingQueue.EnterWaitingQueueInquiry
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
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.codec.JsonJacksonCodec
import org.redisson.config.Config
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

/**
 * 실제 Redis 위에서 "대기열 진입 → 입장 허용" 전체 경로를 검증한다.
 *
 * 핵심 명제: **N개 티켓이 대기 중이고 동시 입장 허용치가 K일 때, 정확히 K개만 ADMITTED가 된다.**
 */
@ExtendWith(SpringExtension::class)
@ContextConfiguration(
    classes = [WaitingQueueRedisAdapterTest.WaitingQueueRedisAdapterTestConfiguration::class],
)
@Testcontainers
class WaitingQueueRedisAdapterTest {
    companion object {
        private const val REDIS_PORT = 6379

        /** `TICKET_OF` 선점 수명. 테스트 한 판이 끝나기 전에 만료되지만 않으면 된다. */
        private const val TICKET_TIME_TO_LIVE_SECONDS = 1_800L
        private val ADMISSION_TIME_TO_LIVE: Duration = Duration.ofMinutes(5)

        @JvmStatic
        @Container
        private val redisContainer =
            GenericContainer(DockerImageName.parse("redis:7.0"))
                .withExposedPorts(REDIS_PORT)

        @JvmStatic
        @DynamicPropertySource
        fun register(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { redisContainer.host }
            registry.add("spring.data.redis.port") { redisContainer.getMappedPort(REDIS_PORT) }
            registry.add("redisson.single-server-config.address") {
                "redis://${redisContainer.host}:${redisContainer.getMappedPort(REDIS_PORT)}"
            }
        }
    }

    @TestConfiguration
    class WaitingQueueRedisAdapterTestConfiguration {
        @Bean
        fun redissonClient(): RedissonClient {
            val address =
                "redis://${redisContainer.host}:${redisContainer.getMappedPort(REDIS_PORT)}"
            val config =
                Config().apply {
                    useSingleServer().address = address
                    codec = JsonJacksonCodec()
                }

            return Redisson.create(config)
        }

        /**
         * Redis가 살아있는 시나리오이므로 폴백은 절대 호출되지 않아야 한다.
         * relaxed 하지 않은 mock이라 실수로 호출되면 테스트가 즉시 깨진다.
         */
        @Bean
        fun waitingQueueFallbackCoordinator() = mockk<WaitingQueueFallbackCoordinator>()

        @Bean
        fun enterWaitingQueueRedisAdapter(
            redissonClient: RedissonClient,
            waitingQueueFallbackCoordinator: WaitingQueueFallbackCoordinator,
        ) = EnterWaitingQueueRedisAdapter(
            redissonClient,
            waitingQueueFallbackCoordinator,
            TICKET_TIME_TO_LIVE_SECONDS,
        )

        @Bean
        fun findWaitingTicketPositionRedisAdapter(
            redissonClient: RedissonClient,
            waitingQueueFallbackCoordinator: WaitingQueueFallbackCoordinator,
        ) = FindWaitingTicketPositionRedisAdapter(redissonClient, waitingQueueFallbackCoordinator)

        @Bean
        fun isTicketAdmittedRedisAdapter(
            redissonClient: RedissonClient,
            waitingQueueFallbackCoordinator: WaitingQueueFallbackCoordinator,
        ) = IsTicketAdmittedRedisAdapter(redissonClient, waitingQueueFallbackCoordinator)

        @Bean
        fun loadWaitingQueueSlotsRedisAdapter(
            redissonClient: RedissonClient,
            waitingQueueFallbackCoordinator: WaitingQueueFallbackCoordinator,
        ) = LoadWaitingQueueSlotsRedisAdapter(redissonClient, waitingQueueFallbackCoordinator)

        @Bean
        fun admitWaitingTicketsRedisAdapter(
            redissonClient: RedissonClient,
            waitingQueueFallbackCoordinator: WaitingQueueFallbackCoordinator,
        ) = AdmitWaitingTicketsRedisAdapter(redissonClient, waitingQueueFallbackCoordinator)
    }

    @Autowired
    private lateinit var enterWaitingQueue: EnterWaitingQueueRedisAdapter

    @Autowired
    private lateinit var findWaitingTicketPosition: FindWaitingTicketPositionRedisAdapter

    @Autowired
    private lateinit var isTicketAdmitted: IsTicketAdmittedRedisAdapter

    @Autowired
    private lateinit var loadWaitingQueueSlots: LoadWaitingQueueSlotsRedisAdapter

    @Autowired
    private lateinit var admitWaitingTickets: AdmitWaitingTicketsRedisAdapter

    private fun newSlot() =
        WaitingQueueSlot(
            restaurantId = UuidGenerator.generate(),
            date = LocalDate.of(2026, 8, 26),
            startTime = LocalTime.of(11, 0),
        )

    /**
     * 진입의 멱등 기준이 사용자이므로 티켓마다 소유자가 하나씩 필요하다.
     * 같은 ticketId로 다시 부르면 같은 userId가 나오므로 "같은 사람의 재진입"이 된다.
     */
    private fun enter(
        slot: WaitingQueueSlot,
        ticketId: String,
    ) = enterWaitingQueue.enter(
        EnterWaitingQueueInquiry(slot = slot, userId = "user-$ticketId", ticketId = ticketId),
    )

    private fun position(
        slot: WaitingQueueSlot,
        ticketId: String,
    ) = findWaitingTicketPosition.query(WaitingTicketInquiry(slot = slot, ticketId = ticketId))

    private fun admitted(
        slot: WaitingQueueSlot,
        ticketId: String,
    ) = isTicketAdmitted.query(AdmissionInquiry(slot = slot, ticketId = ticketId))

    private fun admit(
        slot: WaitingQueueSlot,
        capacity: Int,
        timeToLive: Duration = ADMISSION_TIME_TO_LIVE,
    ) = admitWaitingTickets.admit(
        AdmitInquiry(
            slot = slot,
            capacity = capacity,
            admissionTimeToLive = timeToLive,
        ),
    )

    @DisplayName("대기열에 순서대로 진입하면 진입 순서 그대로 1부터 순번이 매겨진다.")
    @Test
    fun `enter waiting queue then position is assigned in order`() {
        val slot = newSlot()
        val ticketIds = (1..10).map { UuidGenerator.generate() }

        val positions = ticketIds.map { enter(slot, it).position }

        assertEquals((1L..10L).toList(), positions)
        ticketIds.forEachIndexed { index, ticketId ->
            assertEquals(index + 1L, position(slot, ticketId))
            assertTrue(!admitted(slot, ticketId))
        }
    }

    @DisplayName("같은 티켓으로 다시 진입해도 순번이 밀리지 않는다.")
    @Test
    fun `re-enter with same ticket keeps position`() {
        val slot = newSlot()
        val ticketId = UuidGenerator.generate()

        val first = enter(slot, ticketId)
        enter(slot, UuidGenerator.generate())
        val second = enter(slot, ticketId)

        // 티켓과 순번이 둘 다 그대로여야 한다. 사이에 낀 다른 사용자가 뒤로 밀려나야지,
        // 재진입자가 뒤로 가면 안 된다.
        assertEquals(first, second)
        assertEquals(1L, second.position)
    }

    @DisplayName("N개 티켓이 대기 중이고 동시 입장 허용치가 K일 때 정확히 K개만 ADMITTED가 된다.")
    @Test
    fun `admit exactly capacity count of tickets`() {
        val slot = newSlot()
        val totalSize = 50
        val capacity = 10
        val ticketIds = (1..totalSize).map { UuidGenerator.generate() }

        ticketIds.forEach { enter(slot, it) }

        val admittedCount = admit(slot, capacity)

        assertEquals(capacity, admittedCount)
        assertEquals(capacity, ticketIds.count { admitted(slot, it) })

        // 먼저 온 순서(낮은 score)대로 승격된다.
        ticketIds.take(capacity).forEach {
            assertTrue(admitted(slot, it), "먼저 온 티켓은 ADMITTED 여야 한다")
            assertNull(position(slot, it), "ADMITTED 된 티켓은 대기열에서 빠진다")
        }
        ticketIds.drop(capacity).forEachIndexed { index, ticketId ->
            assertTrue(!admitted(slot, ticketId))
            assertEquals(index + 1L, position(slot, ticketId))
        }
    }

    @DisplayName("permit이 모두 소진된 뒤에는 워커를 여러 번 돌려도 추가 입장이 허용되지 않는다.")
    @Test
    fun `admit nothing more once capacity is exhausted`() {
        val slot = newSlot()
        val totalSize = 20
        val capacity = 5
        val ticketIds = (1..totalSize).map { UuidGenerator.generate() }

        ticketIds.forEach { enter(slot, it) }

        val firstCycle = admit(slot, capacity)
        val secondCycle = admit(slot, capacity)
        val thirdCycle = admit(slot, capacity)

        assertEquals(capacity, firstCycle)
        assertEquals(0, secondCycle)
        assertEquals(0, thirdCycle)
        assertEquals(capacity, ticketIds.count { admitted(slot, it) })
    }

    @DisplayName("대기 인원이 허용치보다 적으면 대기 인원 수만큼만 ADMITTED가 된다.")
    @Test
    fun `admit only waiting size when it is smaller than capacity`() {
        val slot = newSlot()
        val waitingSize = 3
        val capacity = 10
        val ticketIds = (1..waitingSize).map { UuidGenerator.generate() }

        ticketIds.forEach { enter(slot, it) }

        assertEquals(waitingSize, admit(slot, capacity))
        assertEquals(waitingSize, ticketIds.count { admitted(slot, it) })
    }

    @DisplayName("여러 스레드가 동시에 대기열에 진입해도 순번이 중복되지 않고, 허용치만큼만 입장한다.")
    @Test
    fun `concurrent enter assigns unique positions and admits capacity only`() {
        val slot = newSlot()
        val totalSize = 60
        val capacity = 12
        val ticketIds = (1..totalSize).map { UuidGenerator.generate() }
        val positions = ConcurrentLinkedQueue<Long>()

        val executor = Executors.newFixedThreadPool(16)
        val startLatch = CountDownLatch(1)
        val endLatch = CountDownLatch(totalSize)

        executor.use {
            ticketIds.forEach { ticketId ->
                executor.submit {
                    try {
                        startLatch.await()
                        positions.add(enter(slot, ticketId).position)
                    } finally {
                        endLatch.countDown()
                    }
                }
            }

            startLatch.countDown()
            assertTrue(endLatch.await(30, SECONDS))
        }

        // 동시에 읽은 순번은 다른 스레드의 ZADD가 반영되기 전 값일 수 있으므로 겹칠 수 있다.
        // 보장되어야 하는 것은 "모든 티켓이 유실 없이 대기열에 들어갔다"는 사실이다.
        assertEquals(totalSize, positions.size)
        assertEquals(
            (1L..totalSize.toLong()).toSet(),
            ticketIds.mapNotNull { position(slot, it) }.toSet(),
        )
        assertEquals(capacity, admit(slot, capacity))
        assertEquals(capacity, ticketIds.count { admitted(slot, it) })
    }

    @DisplayName("대기열에 진입한 슬롯은 입장 허용 워커가 순회할 슬롯 목록에 등록된다.")
    @Test
    fun `entered slot is registered to slot list`() {
        val slot = newSlot()

        enter(slot, UuidGenerator.generate())

        val slots = loadWaitingQueueSlots.query()

        assertNotNull(slots.firstOrNull { it == slot })
    }

    /**
     * 여러 워커(스케줄러 스레드/인스턴스)가 동시에 같은 슬롯을 승격시켜도
     * "permit 획득 → ZPOPMIN" 순서 덕분에 총 승격 수가 허용치를 넘지 않는다.
     */
    @DisplayName("여러 워커가 동시에 승격을 돌려도 총 승격 수는 허용치를 넘지 않는다.")
    @Test
    fun `concurrent admit never exceeds capacity`() {
        val slot = newSlot()
        val totalSize = 200
        val capacity = 20
        val workerSize = 16
        val ticketIds = (1..totalSize).map { UuidGenerator.generate() }
        val admittedCounts = ConcurrentLinkedQueue<Int>()

        ticketIds.forEach { enter(slot, it) }

        val executor = Executors.newFixedThreadPool(workerSize)
        val startLatch = CountDownLatch(1)
        val endLatch = CountDownLatch(workerSize)

        executor.use {
            repeat(workerSize) {
                executor.submit {
                    try {
                        startLatch.await()
                        admittedCounts.add(admit(slot, capacity))
                    } finally {
                        endLatch.countDown()
                    }
                }
            }

            startLatch.countDown()
            assertTrue(endLatch.await(30, SECONDS))
        }

        assertEquals(capacity, admittedCounts.sum(), "총 승격 수는 정확히 허용치여야 한다")
        assertEquals(capacity, ticketIds.count { admitted(slot, it) })
        // 유실 검증: 승격되지 않은 티켓은 전부 대기열에 그대로 남아있어야 한다.
        assertEquals(totalSize - capacity, ticketIds.count { position(slot, it) != null })
    }

    /**
     * permit pool 전체에 TTL을 거는 구현(= `RSemaphore.trySetPermits(capacity, ttl)`)은
     * capacity를 "TTL 주기마다 초기화되는 예산"으로 만들어 경계에서 정원을 두 배로 늘린다.
     *
     * 타임라인(허용치 5, TTL 4초):
     * - t=0.0s 티켓 1장 승격 → pool 생성. 잘못된 구현은 여기서 pool 키 TTL이 4초로 고정된다.
     * - t=3.0s 남은 4장 승격 → 살아있는 ADMITTED 5장 (t=7s 까지 유효).
     * - t=4.6s pool 키 만료 직후. 잘못된 구현은 permit 5개가 통째로 복구되어 5장을 더 승격시키고
     *   살아있는 ADMITTED가 9장이 된다. permit 단위 lease면 t=0 승격분 1장만 회수된다.
     */
    @DisplayName("permit은 입장 허용 건별 lease라서 TTL 경계에서 정원이 두 배가 되지 않는다.")
    @Test
    fun `permit lease is per admission so capacity does not double at ttl boundary`() {
        val slot = newSlot()
        val capacity = 5
        val timeToLive = Duration.ofSeconds(4)
        val firstTicketId = UuidGenerator.generate()
        val laterTicketIds = (1..10).map { UuidGenerator.generate() }

        enter(slot, firstTicketId)
        assertEquals(1, admit(slot, capacity, timeToLive))

        Thread.sleep(3_000)

        laterTicketIds.forEach { enter(slot, it) }
        assertEquals(capacity - 1, admit(slot, capacity, timeToLive))

        Thread.sleep(1_600)

        admit(slot, capacity, timeToLive)

        val liveAdmitted = (laterTicketIds + firstTicketId).count { admitted(slot, it) }

        assertTrue(
            liveAdmitted <= capacity,
            "살아있는 ADMITTED는 언제나 허용치 이하여야 하는데 ${liveAdmitted}개였다",
        )
    }

    @DisplayName("이미 입장이 허용된 티켓으로 다시 진입해도 대기열에 다시 붙지 않는다.")
    @Test
    fun `re-enter after admission does not requeue`() {
        val slot = newSlot()
        val ticketId = UuidGenerator.generate()
        val capacity = 1

        enter(slot, ticketId)
        assertEquals(capacity, admit(slot, capacity))
        assertTrue(admitted(slot, ticketId))

        val reEnteredPosition = enter(slot, ticketId).position

        assertEquals(ADMITTED_POSITION, reEnteredPosition)
        assertNull(position(slot, ticketId), "ADMITTED 티켓은 대기열에 다시 들어가지 않는다")
        assertTrue(admitted(slot, ticketId))
    }
}
