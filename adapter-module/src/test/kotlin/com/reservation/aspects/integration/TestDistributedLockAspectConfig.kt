package com.reservation.aspects.integration

import com.reservation.redis.redisson.lock.AcquireLockTemplate
import com.reservation.redis.redisson.lock.CheckLockTemplate
import com.reservation.redis.redisson.lock.UnlockLockTemplate
import com.reservation.redis.redisson.lock.fair.adapter.AcquireFairLockAdapter
import com.reservation.redis.redisson.lock.fair.adapter.CheckFairLockAdapter
import com.reservation.redis.redisson.lock.fair.adapter.UnlockFairLockAdapter
import com.reservation.redis.redisson.lock.general.adapter.AcquireLockAdapter
import com.reservation.redis.redisson.lock.general.adapter.CheckLockAdapter
import com.reservation.redis.redisson.lock.general.adapter.UnlockLockAdapter
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class TestDistributedLockAspectConfig {
    @Bean
    fun acquireFairLockAdapter(): AcquireLockTemplate = mockk<AcquireFairLockAdapter>()

    @Bean
    fun checkFairLockAdapter(): CheckLockTemplate = mockk<CheckFairLockAdapter>()

    @Bean
    fun unlockFairLockAdapter(): UnlockLockTemplate = mockk<UnlockFairLockAdapter>()

    @Bean
    fun acquireLockAdapter(): AcquireLockTemplate = mockk<AcquireLockAdapter>()

    @Bean
    fun checkLockAdapter(): CheckLockTemplate = mockk<CheckLockAdapter>()

    @Bean
    fun unlockLockAdapter(): UnlockLockTemplate = mockk<UnlockLockAdapter>()
}
