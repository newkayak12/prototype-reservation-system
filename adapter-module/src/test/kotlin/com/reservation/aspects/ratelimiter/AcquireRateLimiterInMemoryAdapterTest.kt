package com.reservation.aspects.ratelimiter

import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.api.jqwik.JavaTypeArbitraryGenerator
import com.navercorp.fixturemonkey.api.jqwik.JqwikPlugin
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.enumeration.RateLimitType.WHOLE
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.BucketLiveTimeSettings
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.MaximumWaitSettings
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.RateLimiterSettings
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.RateSettings
import com.reservation.redis.redisson.ratelimit.adapter.AcquireRateLimitInMemoryAdapter
import io.mockk.junit5.MockKExtension
import net.jqwik.api.Arbitraries
import net.jqwik.api.arbitraries.StringArbitrary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.HOURS
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS

@ExtendWith(MockKExtension::class)
class AcquireRateLimiterInMemoryAdapterTest {
    private val acquireRateLimitInMemoryAdapter = AcquireRateLimitInMemoryAdapter()

    private lateinit var pureMonkey: FixtureMonkey

    @BeforeEach
    fun setUp() {
        pureMonkey =
            FixtureMonkeyFactory
                .giveMePureMonkey()
                .plugin(
                    JqwikPlugin().javaTypeArbitraryGenerator(
                        object : JavaTypeArbitraryGenerator {
                            override fun strings(): StringArbitrary {
                                return Arbitraries.strings().alpha().ofLength(32)
                            }
                        },
                    ),
                )
                .build()
    }

    @DisplayName("rateLimiter의 최대 개수를 1000L으로 설정하고 1개를 acquire해서 availablePermit을 구하면 999가 된다.")
    @Test
    fun `set 1000L to rate limiter and acquire then return 999`() {
        val key = pureMonkey.giveMeOne<String>()
        val totalSize = 1000L
        val expected = totalSize - 1

        val rateLimiterSettings = RateLimiterSettings(key, WHOLE)
        val maximumWaitSettings = MaximumWaitSettings(10, SECONDS)
        val rateSettings = RateSettings(totalSize, 1, MINUTES)
        val bucketLiveTimeSettings = BucketLiveTimeSettings(1, HOURS)

        acquireRateLimitInMemoryAdapter.tryAcquire(
            rateLimiterSettings,
            maximumWaitSettings,
            rateSettings,
            bucketLiveTimeSettings,
        )

        val availablePermits =
            acquireRateLimitInMemoryAdapter.availablePermits(
                rateLimiterSettings,
                maximumWaitSettings,
                rateSettings,
                bucketLiveTimeSettings,
            )

        assertEquals(expected, availablePermits)
    }

    @DisplayName(
        "rateLimiter의 최대 개수를 1000L으로 설정하고 10개를 acquire해서" +
            " availablePermit을 구하면 990이 된다.",
    )
    @Test
    fun `set 1000 to rate limiter and acquire 10 times then return 990`() {
        val availableProcess = Runtime.getRuntime().availableProcessors()
        val multiplier = 4
        val executor = Executors.newFixedThreadPool(availableProcess * multiplier)
        val key = pureMonkey.giveMeOne<String>()
        val totalSize = 1000L
        val repeatSize = 10
        val expected = totalSize - repeatSize

        val startLatch = CountDownLatch(1)
        val endLatch = CountDownLatch(repeatSize)

        val rateLimiterSettings = RateLimiterSettings(key, WHOLE)
        val maximumWaitSettings = MaximumWaitSettings(10, SECONDS)
        val rateSettings = RateSettings(totalSize, 1, MINUTES)
        val bucketLiveTimeSettings = BucketLiveTimeSettings(1, HOURS)

        executor.use {
            repeat(repeatSize) {
                executor.submit {
                    try {
                        startLatch.await()
                        acquireRateLimitInMemoryAdapter.tryAcquire(
                            rateLimiterSettings,
                            maximumWaitSettings,
                            rateSettings,
                            bucketLiveTimeSettings,
                        )
                    } finally {
                        endLatch.countDown()
                    }
                }
            }

            startLatch.countDown()
            endLatch.await(5, SECONDS)

        }


        val availablePermits =
            acquireRateLimitInMemoryAdapter.availablePermits(
                rateLimiterSettings,
                maximumWaitSettings,
                rateSettings,
                bucketLiveTimeSettings,
            )

        assertEquals(expected, availablePermits)
    }

    @DisplayName(
        "rateLimiter의 최대 개수를 1000으로 설정하고 1000개를 acquire해서" +
            " availablePermit을 구하면 0이 된다.",
    )
    @Test
    fun `set 1000 to rate limiter and acquire 1000 times then return 0`() {
        val availableProcess = Runtime.getRuntime().availableProcessors()
        val multiplier = 4
        val executor = Executors.newFixedThreadPool(availableProcess * multiplier)
        val key = pureMonkey.giveMeOne<String>()
        val totalSize = 1000L
        val repeatSize = 1000
        val expected = totalSize - repeatSize

        val startLatch = CountDownLatch(1)
        val endLatch = CountDownLatch(repeatSize)

        val rateLimiterSettings = RateLimiterSettings(key, WHOLE)
        val maximumWaitSettings = MaximumWaitSettings(10, SECONDS)
        val rateSettings = RateSettings(totalSize, 1, MINUTES)
        val bucketLiveTimeSettings = BucketLiveTimeSettings(1, HOURS)

        executor.use {

            repeat(repeatSize) {
                executor.submit {
                    try {
                        startLatch.await()
                        acquireRateLimitInMemoryAdapter.tryAcquire(
                            rateLimiterSettings,
                            maximumWaitSettings,
                            rateSettings,
                            bucketLiveTimeSettings,
                        )
                    } finally {
                        endLatch.countDown()
                    }
                }
            }

            startLatch.countDown()
            endLatch.await(5, SECONDS)

        }



        val availablePermits =
            acquireRateLimitInMemoryAdapter.availablePermits(
                rateLimiterSettings,
                maximumWaitSettings,
                rateSettings,
                bucketLiveTimeSettings,
            )

        assertEquals(expected, availablePermits)
    }

    @DisplayName(
        "rateLimiter의 최대 개수를 1000으로 설정하고 2000개를 acquire해서" +
            " availablePermit을 구하면 0이 된다.",
    )
    @Test
    fun `set 1000 to rate limiter and acquire 2000 times then return 0`() {
        val availableProcess = Runtime.getRuntime().availableProcessors()
        val multiplier = 4
        val executor = Executors.newFixedThreadPool(availableProcess * multiplier)
        val key = pureMonkey.giveMeOne<String>()
        val totalSize = 1000L
        val repeatSize = 2000
        val expected = 0L

        val startLatch = CountDownLatch(1)
        val endLatch = CountDownLatch(repeatSize)

        val rateLimiterSettings = RateLimiterSettings(key, WHOLE)
        val maximumWaitSettings = MaximumWaitSettings(10, SECONDS)
        val rateSettings = RateSettings(totalSize, 1, MINUTES)
        val bucketLiveTimeSettings = BucketLiveTimeSettings(1, HOURS)

        executor.use {
            repeat(repeatSize) {
                executor.submit {
                    try {
                        startLatch.await()
                        acquireRateLimitInMemoryAdapter.tryAcquire(
                            rateLimiterSettings,
                            maximumWaitSettings,
                            rateSettings,
                            bucketLiveTimeSettings,
                        )
                    } finally {
                        endLatch.countDown()
                    }
                }
            }

            startLatch.countDown()
            endLatch.await(5, SECONDS)

        }


        val availablePermits =
            acquireRateLimitInMemoryAdapter.availablePermits(
                rateLimiterSettings,
                maximumWaitSettings,
                rateSettings,
                bucketLiveTimeSettings,
            )

        assertEquals(expected, availablePermits)
    }
}
