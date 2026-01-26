package com.reservation.aspects.ratelimiter

import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.api.jqwik.JavaTypeArbitraryGenerator
import com.navercorp.fixturemonkey.api.jqwik.JqwikPlugin
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.aspects.ratelimiter.AcquireRateLimiterRedisAdapterTest.AcquireRateLimiterRedisAdapterTestConfiguration
import com.reservation.enumeration.RateLimitType.WHOLE
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.BucketLiveTimeSettings
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.MaximumWaitSettings
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.RateLimiterSettings
import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate.RateSettings
import com.reservation.redis.redisson.ratelimit.adapter.AcquireRateLimitRedisAdapter
import net.jqwik.api.Arbitraries
import net.jqwik.api.arbitraries.StringArbitrary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.redisson.Redisson
import org.redisson.api.RedissonClient
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.HOURS
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [AcquireRateLimiterRedisAdapterTestConfiguration::class])
@Testcontainers
class AcquireRateLimiterRedisAdapterTest {
    companion object {
        @JvmStatic
        @Container
        private val redisContainer =
            GenericContainer(DockerImageName.parse("redis:7.0"))
                .withExposedPorts(6379)

        @JvmStatic
        @DynamicPropertySource
        fun register(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { redisContainer.host }
            registry.add("spring.data.redis.port") {
                redisContainer.getMappedPort(6379)
            }
            registry.add("redisson.single-server-config.address") {
                "redis://${redisContainer.host}:${redisContainer.getMappedPort(6379)}"
            }
        }
    }

    @TestConfiguration
    class AcquireRateLimiterRedisAdapterTestConfiguration {
        @Bean
        fun redissonClient(): RedissonClient {
            val address =
                "redis://${redisContainer.getHost()}:${redisContainer.getMappedPort(6379)}"

            val config =
                Config().apply {
                    this.useSingleServer()
                        .setAddress(address)
                }

            return Redisson.create(config)
        }

        @Bean
        fun acquireRateLimitRedisAdapter(redissonClient: RedissonClient) =
            AcquireRateLimitRedisAdapter(redissonClient)
    }

    @Autowired
    private lateinit var acquireRateLimitRedisAdapter: AcquireRateLimitRedisAdapter

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

        acquireRateLimitRedisAdapter.tryAcquire(
            rateLimiterSettings,
            maximumWaitSettings,
            rateSettings,
            bucketLiveTimeSettings,
        )

        val availablePermits =
            acquireRateLimitRedisAdapter.availablePermits(
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
    fun `set 1000 to rate limiter and acquire then return 990`() {
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
                        acquireRateLimitRedisAdapter.tryAcquire(
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
            acquireRateLimitRedisAdapter.availablePermits(
                rateLimiterSettings,
                maximumWaitSettings,
                rateSettings,
                bucketLiveTimeSettings,
            )

        assertEquals(expected, availablePermits)
    }
}
