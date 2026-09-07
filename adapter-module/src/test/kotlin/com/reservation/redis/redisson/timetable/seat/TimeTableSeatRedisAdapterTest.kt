package com.reservation.redis.redisson.timetable.seat

import com.reservation.redis.redisson.timetable.seat.adapter.AcquireTimeTableSeatRedisAdapter
import com.reservation.redis.redisson.timetable.seat.adapter.ReleaseTimeTableSeatRedisAdapter
import com.reservation.timetable.port.output.AcquireTimeTableSeat.SeatAcquisition
import com.reservation.timetable.port.output.AcquireTimeTableSeat.SeatAcquisition.ACQUIRED
import com.reservation.timetable.port.output.AcquireTimeTableSeat.SeatAcquisition.DUPLICATED
import com.reservation.timetable.port.output.AcquireTimeTableSeat.SeatAcquisition.SOLD_OUT
import com.reservation.timetable.port.output.AcquireTimeTableSeat.SeatInquiry
import com.reservation.timetable.port.output.ReleaseTimeTableSeat.SeatReleaseInquiry
import com.reservation.utilities.generator.uuid.UuidGenerator
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

/**
 * 실제 Redis 위에서 좌석 원자 차감을 검증한다.
 *
 * 이 테스트가 Phase 2의 존재 이유다. 분산락을 걷어낸 근거가 "Lua 스크립트가 원자적이니
 * 줄을 세우지 않아도 된다"인데, 그 명제가 실제로 성립하는지는 **진짜 동시 요청을 때려 봐야**
 * 알 수 있다. mock으로는 이 성질을 증명할 수 없다.
 *
 * 핵심 명제: **좌석이 K개인 슬롯에 N명이 동시에 달려들면 정확히 K명만 성공한다.**
 */
@ExtendWith(SpringExtension::class)
@ContextConfiguration(
    classes = [TimeTableSeatRedisAdapterTest.SeatRedisAdapterTestConfiguration::class],
)
@Testcontainers
class TimeTableSeatRedisAdapterTest {
    companion object {
        private const val REDIS_PORT = 6379
        private const val SEAT_TIME_TO_LIVE_SECONDS = 3_600L
        private const val DEDUP_TIME_TO_LIVE_SECONDS = 3_600L

        /** 좌석 수보다 요청이 훨씬 많아야 경합이 실제로 일어난다. */
        private const val SEAT_SIZE = 30
        private const val CONTENDER_SIZE = 300
        private const val POOL_SIZE = 32

        @JvmStatic
        @Container
        private val redisContainer =
            GenericContainer(DockerImageName.parse("redis:7.0"))
                .withExposedPorts(REDIS_PORT)
    }

    @TestConfiguration
    class SeatRedisAdapterTestConfiguration {
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

        @Bean
        fun acquireTimeTableSeatRedisAdapter(redissonClient: RedissonClient) =
            AcquireTimeTableSeatRedisAdapter(
                redissonClient,
                SEAT_TIME_TO_LIVE_SECONDS,
                DEDUP_TIME_TO_LIVE_SECONDS,
            )

        @Bean
        fun releaseTimeTableSeatRedisAdapter(redissonClient: RedissonClient) =
            ReleaseTimeTableSeatRedisAdapter(redissonClient)
    }

    @Autowired
    private lateinit var acquire: AcquireTimeTableSeatRedisAdapter

    @Autowired
    private lateinit var release: ReleaseTimeTableSeatRedisAdapter

    private val date = LocalDate.of(2026, 8, 26)
    private val startTime = LocalTime.of(11, 0)

    /** 테스트마다 새 매장을 써서 키가 겹치지 않게 한다. */
    private fun newRestaurantId() = UuidGenerator.generate()

    private fun acquireFor(
        restaurantId: String,
        userId: String,
        availableSeats: Int = SEAT_SIZE,
    ): SeatAcquisition =
        acquire.acquire(
            SeatInquiry(
                restaurantId = restaurantId,
                date = date,
                startTime = startTime,
                userId = userId,
                availableSeats = availableSeats,
            ),
        )

    private fun releaseFor(
        restaurantId: String,
        userId: String,
    ) = release.release(
        SeatReleaseInquiry(
            restaurantId = restaurantId,
            date = date,
            startTime = startTime,
            userId = userId,
        ),
    )

    @DisplayName("좌석보다 훨씬 많은 사용자가 동시에 달려들어도 좌석 수만큼만 성공한다.")
    @Test
    fun `concurrent acquire never exceeds seat size`() {
        val restaurantId = newRestaurantId()
        val results = ConcurrentLinkedQueue<SeatAcquisition>()
        val startLatch = CountDownLatch(1)
        val endLatch = CountDownLatch(CONTENDER_SIZE)

        val executor = Executors.newFixedThreadPool(POOL_SIZE)
        executor.use {
            repeat(CONTENDER_SIZE) {
                executor.submit {
                    try {
                        startLatch.await()
                        results.add(acquireFor(restaurantId, UuidGenerator.generate()))
                    } finally {
                        endLatch.countDown()
                    }
                }
            }

            startLatch.countDown()
            endLatch.await(30, SECONDS)
        }

        assertEquals(CONTENDER_SIZE, results.size)
        assertEquals(SEAT_SIZE, results.count { it == ACQUIRED })
        assertEquals(CONTENDER_SIZE - SEAT_SIZE, results.count { it == SOLD_OUT })
        // 서로 다른 사용자이므로 중복 판정은 하나도 나오면 안 된다.
        assertEquals(0, results.count { it == DUPLICATED })
    }

    @DisplayName("같은 사용자가 동시에 여러 번 요청해도 한 자리만 가져간다.")
    @Test
    fun `same user takes exactly one seat even when racing with itself`() {
        val restaurantId = newRestaurantId()
        val userId = UuidGenerator.generate()
        val attempts = 50
        val results = ConcurrentLinkedQueue<SeatAcquisition>()
        val startLatch = CountDownLatch(1)
        val endLatch = CountDownLatch(attempts)

        val executor = Executors.newFixedThreadPool(POOL_SIZE)
        executor.use {
            repeat(attempts) {
                executor.submit {
                    try {
                        startLatch.await()
                        results.add(acquireFor(restaurantId, userId))
                    } finally {
                        endLatch.countDown()
                    }
                }
            }

            startLatch.countDown()
            endLatch.await(30, SECONDS)
        }

        // 이것이 DEDUP 마커의 존재 이유다. 마커가 없으면 한 사람이 좌석을 몽땅 쓸어간다.
        assertEquals(1, results.count { it == ACQUIRED })
        assertEquals(attempts - 1, results.count { it == DUPLICATED })
    }

    @DisplayName("되돌리면 좌석이 회수되고 같은 사용자가 다시 시도할 수 있다.")
    @Test
    fun `release gives the seat back and allows retry`() {
        val restaurantId = newRestaurantId()
        val userId = UuidGenerator.generate()

        // 좌석 1개짜리 슬롯을 혼자 다 먹는다.
        assertEquals(ACQUIRED, acquireFor(restaurantId, userId, availableSeats = 1))
        // 다른 사람은 품절.
        assertEquals(SOLD_OUT, acquireFor(restaurantId, UuidGenerator.generate()))
        // 본인 재시도는 중복.
        assertEquals(DUPLICATED, acquireFor(restaurantId, userId))

        releaseFor(restaurantId, userId)

        // 자리가 돌아왔으므로 다른 사람이 가져갈 수 있다.
        assertEquals(ACQUIRED, acquireFor(restaurantId, UuidGenerator.generate()))
    }

    @DisplayName("품절로 거절된 요청은 좌석 카운터를 갉아먹지 않는다.")
    @Test
    fun `sold out attempts do not drain the counter`() {
        val restaurantId = newRestaurantId()

        repeat(SEAT_SIZE) { assertEquals(ACQUIRED, acquireFor(restaurantId, "taker-$it")) }
        // 품절 상태에서 계속 두드려도 카운터가 음수로 흘러내리면 안 된다.
        repeat(20) { assertEquals(SOLD_OUT, acquireFor(restaurantId, "late-$it")) }

        // 한 자리 되돌리면 정확히 한 명만 더 들어갈 수 있어야 한다.
        releaseFor(restaurantId, "taker-0")
        assertEquals(ACQUIRED, acquireFor(restaurantId, "extra-1"))
        assertEquals(SOLD_OUT, acquireFor(restaurantId, "extra-2"))
    }
}
