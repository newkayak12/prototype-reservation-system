package com.reservation.aspects.integration

import com.reservation.redis.redisson.ratelimit.AcquireRateLimiterTemplate
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class TestRateLimiterAspectConfig {
    @Bean
    fun rateLimiterTemplate(): AcquireRateLimiterTemplate = mockk<AcquireRateLimiterTemplate>()
}
