package com.reservation.aspects.ratelimiter

import com.reservation.aspects.ratelimiter.RateLimiterAspectTest.InitializeMockContext
import com.reservation.config.aspect.RateLimiterAspect
import com.reservation.config.aspect.SpelParser
import com.reservation.enumeration.RateLimiterTemplateState.ACTIVATED
import com.reservation.enumeration.RateLimiterTemplateState.DEACTIVATED
import com.reservation.redis.redisson.ratelimit.adapter.AcquireRateLimitInMemoryAdapter
import com.reservation.redis.redisson.ratelimit.adapter.AcquireRateLimitRedisAdapter
import com.reservation.timetable.exceptions.TooManyCreateTimeTableOccupancyRequestException
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.redisson.client.RedisException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(value = [SpringExtension::class])
@ContextConfiguration(classes = [InitializeMockContext::class])
class RateLimiterAspectTest {
    @Autowired
    private lateinit var inMemoryAdapter: AcquireRateLimitInMemoryAdapter

    @Autowired
    private lateinit var redisAdapter: AcquireRateLimitRedisAdapter

    @Autowired
    private lateinit var rateLimiterAspectTestComponent: RateLimiterAspectTestComponent

    @EnableAspectJAutoProxy
    @TestConfiguration
    class InitializeMockContext {
        @Bean
        fun spelParser(): SpelParser = SpelParser()

        @Bean
        fun acquireRateLimitInMemoryAdapter() = mockk<AcquireRateLimitInMemoryAdapter>()

        @Bean
        fun acquireRateLimitRedisAdapter() = mockk<AcquireRateLimitRedisAdapter>()

        @Bean
        fun rateLimiterAspect(
            spelParser: SpelParser,
            acquireRateLimitInMemoryAdapter: AcquireRateLimitInMemoryAdapter,
            acquireRateLimitRedisAdapter: AcquireRateLimitRedisAdapter,
        ) = RateLimiterAspect(
            spelParser,
            acquireRateLimitInMemoryAdapter,
            acquireRateLimitRedisAdapter,
        )

        @Bean
        fun rateLimiterAspectTestComponent() = RateLimiterAspectTestComponent()
    }

    @BeforeEach
    fun setUp() {
        clearMocks(
            inMemoryAdapter,
            redisAdapter,
        )
    }

    /**
     * Redis로 RateLimiter를 받아서 메소드 실행에 성공한다.
     * Redis의 tryAcquire 1회 호출되며
     *
     * Redis의 tryAcquire이 1회 호출된다.
     * InMemory는 tryAcquire이 0회 호출된다.
     * syncAcquiredResult는 1회 실행된다.
     */
    @DisplayName("RateLimiter를 Redis로 실행하여 정상적으로 요청에 성공한다.")
    @Test
    fun `call rate limiter as Redis successfully`() {
        val command = "SUCCESS"

        every { redisAdapter.status() } returns ACTIVATED
        every { redisAdapter.tryAcquire(any(), any(), any(), any()) } returns true
        every { inMemoryAdapter.syncAcquiredResult(any(), any(), any(), any()) } just Runs

        val result = rateLimiterAspectTestComponent.rateLimiterTest(command)
        assertThat(result).isEqualTo(command)

        verify(exactly = 1) { redisAdapter.status() }
        verify(exactly = 1) { redisAdapter.tryAcquire(any(), any(), any(), any()) }
        verify(exactly = 1) { inMemoryAdapter.syncAcquiredResult(any(), any(), any(), any()) }
        verify(exactly = 0) { inMemoryAdapter.tryAcquire(any(), any(), any(), any()) }
    }

    /**
     * Redis RateLimiter를 받아서 메소드 실행에 성공하지만
     * Redis의 tryAcquire에서 횟수가 남지 않아서
     * TooManyCreateTimeTableOccupancyRequestException가 발생한다.
     *
     * Redis의 tryAcquire이 1회 호출된다.
     * InMemory는 tryAcquire이 0회 호출된다.
     * syncAcquiredResult는 0회 실행된다.
     */
    @DisplayName("RateLimiter를 Redis로 실행하여 TooManyCreateTimeTableOccupancyRequestException가 발생한다")
    @Test
    fun `call rate limiter as Redis throw TooManyCreateTimeTableOccupancyRequestException`() {
        val command = "SUCCESS"

        every { redisAdapter.status() } returns ACTIVATED
        every { redisAdapter.tryAcquire(any(), any(), any(), any()) } returns false
        every { inMemoryAdapter.syncAcquiredResult(any(), any(), any(), any()) } just Runs

        assertThrows<TooManyCreateTimeTableOccupancyRequestException> {
            rateLimiterAspectTestComponent.rateLimiterTest(command)
        }

        verify(exactly = 1) { redisAdapter.status() }
        verify(exactly = 1) { redisAdapter.tryAcquire(any(), any(), any(), any()) }
        verify(exactly = 0) { inMemoryAdapter.syncAcquiredResult(any(), any(), any(), any()) }
        verify(exactly = 0) { inMemoryAdapter.tryAcquire(any(), any(), any(), any()) }
    }

    /**
     * Redis RateLimiter를 받아서 메소드 실행에 성공하지만
     * Redis의 tryAcquire에서 Redis 통신 불량으로
     * RedisException가 발생한다.
     * InMemory로 fallback 하여 성공한다.
     *
     * Redis의 tryAcquire이 1회 호출된다.
     * InMemory는 tryAcquire이 1회 호출된다.
     * syncAcquiredResult는 0회 실행된다.
     */

    @DisplayName("RateLimiter를 Redis로 실행하여 RedisException가 발생한다 그리고 InMemory로 fallback한다.")
    @Test
    fun `call rate limiter as Redis throw RedisException then fallback success`() {
        val command = "SUCCESS"

        every { redisAdapter.status() } returns ACTIVATED
        every { redisAdapter.tryAcquire(any(), any(), any(), any()) } throws RedisException("")
        every { inMemoryAdapter.tryAcquire(any(), any(), any(), any()) } returns true

        val result = rateLimiterAspectTestComponent.rateLimiterTest(command)
        assertThat(result).isEqualTo(command)

        verify(exactly = 1) { redisAdapter.status() }
        verify(exactly = 1) { redisAdapter.tryAcquire(any(), any(), any(), any()) }
        verify(exactly = 0) { inMemoryAdapter.syncAcquiredResult(any(), any(), any(), any()) }
        verify(exactly = 1) { inMemoryAdapter.tryAcquire(any(), any(), any(), any()) }
    }

    /**
     * Redis RateLimiter를 받아서 메소드 실행에 성공하지만
     * Redis의 tryAcquire에서 Redis 통신 불량으로
     * RedisException가 발생한다.
     * InMemory로 fallback 하여 실패하며 TooManyCreateTimeTableOccupancyRequestException가 발생한다
     *
     * Redis의 tryAcquire이 1회 호출된다.
     * InMemory는 tryAcquire이 1회 호출된다.
     * syncAcquiredResult는 0회 실행된다.
     */

    @DisplayName(
        "RateLimiter를 Redis로 실행하여 RedisException가 발생한다 " +
            "그리고 InMemory 실패해 TooManyCreateTimeTableOccupancyRequestException가 발생한다",
    )
    @Test
    fun `call rate limiter as Redis throw RedisException then fallback but failed`() {
        val command = "SUCCESS"

        every { redisAdapter.status() } returns ACTIVATED
        every { redisAdapter.tryAcquire(any(), any(), any(), any()) } throws RedisException("")
        every { inMemoryAdapter.tryAcquire(any(), any(), any(), any()) } returns false

        assertThrows<TooManyCreateTimeTableOccupancyRequestException> {
            rateLimiterAspectTestComponent.rateLimiterTest(command)
        }

        verify(exactly = 1) { redisAdapter.status() }
        verify(exactly = 1) { redisAdapter.tryAcquire(any(), any(), any(), any()) }
        verify(exactly = 0) { inMemoryAdapter.syncAcquiredResult(any(), any(), any(), any()) }
        verify(exactly = 1) { inMemoryAdapter.tryAcquire(any(), any(), any(), any()) }
    }

    /**
     * InMemory로 RateLimiter를 받아서 메소드 실행에 성공한다.
     * InMemory의 tryAcquire 1회 호출되며
     *
     * Redis의 tryAcquire이 0회 호출된다.
     * InMemory는 tryAcquire이 1회 호출된다.
     * syncAcquiredResult는 0회 실행된다.
     */
    @DisplayName("RateLimiter를 InMemory로 실행하여 RateLimiter 획득에 성공한다.")
    @Test
    fun `call rate limiter as InMemory`() {
        val command = "SUCCESS"

        every { redisAdapter.status() } returns DEACTIVATED
        every { inMemoryAdapter.tryAcquire(any(), any(), any(), any()) } returns true

        val result = rateLimiterAspectTestComponent.rateLimiterTest(command)
        assertThat(result).isEqualTo(command)

        verify(exactly = 1) { redisAdapter.status() }
        verify(exactly = 0) { redisAdapter.tryAcquire(any(), any(), any(), any()) }
        verify(exactly = 0) { inMemoryAdapter.syncAcquiredResult(any(), any(), any(), any()) }
        verify(exactly = 1) { inMemoryAdapter.tryAcquire(any(), any(), any(), any()) }
    }

    /**
     * InMemory RateLimiter를 받아서 메소드 실행에 성공하지만
     * InMemory의 tryAcquire에서 횟수가 남지 않아서
     * TooManyCreateTimeTableOccupancyRequestException가 발생한다.
     *
     * Redis의 tryAcquire이 0회 호출된다.
     * InMemory는 tryAcquire이 1회 호출된다.
     * syncAcquiredResult는 0회 실행된다.
     */
    @DisplayName(
        "RateLimiter를 InMemory로 실행하여 획득에 실패해서" +
            " TooManyCreateTimeTableOccupancyRequestException가 발생한다.",
    )
    @Test
    fun `call rate limiter as InMemory throw TooManyCreateTimeTableOccupancyRequestException`() {
        val command = "SUCCESS"

        every { redisAdapter.status() } returns DEACTIVATED
        every { inMemoryAdapter.tryAcquire(any(), any(), any(), any()) } returns false

        assertThrows<TooManyCreateTimeTableOccupancyRequestException> {
            rateLimiterAspectTestComponent.rateLimiterTest(command)
        }

        verify(exactly = 1) { redisAdapter.status() }
        verify(exactly = 0) { redisAdapter.tryAcquire(any(), any(), any(), any()) }
        verify(exactly = 0) { inMemoryAdapter.syncAcquiredResult(any(), any(), any(), any()) }
        verify(exactly = 1) { inMemoryAdapter.tryAcquire(any(), any(), any(), any()) }
    }
}
