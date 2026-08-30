package com.reservation.aspects.distributedlock

import com.reservation.config.aspect.DistributedLockAspect
import com.reservation.config.aspect.SpelParser
import com.reservation.queue.port.input.AdmitSlotUseCase
import com.reservation.queue.port.input.AdmitWaitingQueueUseCase
import com.reservation.queue.port.output.AdmitWaitingTickets
import com.reservation.queue.port.output.LoadWaitingQueueSlots
import com.reservation.queue.usecase.AdmitSlotService
import com.reservation.queue.usecase.AdmitWaitingQueueService
import com.reservation.queue.vo.WaitingQueueSlot
import com.reservation.redis.redisson.lock.AcquireLockTemplate
import com.reservation.redis.redisson.lock.CheckLockTemplate
import com.reservation.redis.redisson.lock.UnlockLockTemplate
import com.reservation.redis.redisson.lock.fair.FairLockRedisCoordinator
import com.reservation.redis.redisson.lock.fair.adapter.AcquireFairLockAdapter
import com.reservation.redis.redisson.lock.fair.adapter.CheckFairLockAdapter
import com.reservation.redis.redisson.lock.fair.adapter.UnlockFairLockAdapter
import com.reservation.redis.redisson.lock.general.GeneralLockRedisCoordinator
import com.reservation.redis.redisson.lock.general.adapter.AcquireLockAdapter
import com.reservation.redis.redisson.lock.general.adapter.CheckLockAdapter
import com.reservation.redis.redisson.lock.general.adapter.UnlockLockAdapter
import com.reservation.redis.redisson.lock.named.NamedLockCoordinator
import com.reservation.redis.redisson.lock.named.adapter.AcquireNamedLockAdapter
import com.reservation.redis.redisson.lock.named.adapter.UnlockNamedLockAdapter
import com.reservation.timetable.exceptions.TooManyRequestHasBeenComeSimultaneouslyException
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDate
import java.time.LocalTime

/**
 * 입장 허용 워커가 "한 번에 한 인스턴스"만 도는지 검증한다.
 *
 * ShedLock을 새로 들이지 않고 기존 [DistributedLockAspect]를 재사용했으므로,
 * 실제로 락을 잡지 못한 인스턴스가 승격 로직을 **한 줄도 실행하지 않고** 튕겨 나오는지가
 * 이 결정의 유효성을 좌우한다.
 */
@ExtendWith(SpringExtension::class)
@ContextConfiguration(
    classes = [AdmitWaitingQueueDistributedLockTest.AdmissionWorkerLockContext::class],
)
class AdmitWaitingQueueDistributedLockTest {
    companion object {
        private const val CAPACITY = 30
        private const val TIME_TO_LIVE_SECONDS = 300L
    }

    @Autowired
    private lateinit var admitWaitingQueueUseCase: AdmitWaitingQueueUseCase

    @Autowired
    private lateinit var loadWaitingQueueSlots: LoadWaitingQueueSlots

    @Autowired
    private lateinit var admitWaitingTickets: AdmitWaitingTickets

    @Autowired
    @Qualifier(value = "acquireLockAdapter")
    private lateinit var acquireLockAdapter: AcquireLockTemplate

    @Autowired
    @Qualifier(value = "checkLockAdapter")
    private lateinit var checkLockAdapter: CheckLockTemplate

    @Autowired
    @Qualifier(value = "unlockLockAdapter")
    private lateinit var unlockLockAdapter: UnlockLockTemplate

    @BeforeEach
    fun init() {
        clearAllMocks()
    }

    @TestConfiguration
    @EnableAspectJAutoProxy
    class AdmissionWorkerLockContext {
        @Bean
        fun spelParser() = SpelParser()

        @Bean
        fun loadWaitingQueueSlots() = mockk<LoadWaitingQueueSlots>()

        @Bean
        fun admitWaitingTickets() = mockk<AdmitWaitingTickets>()

        @Bean("acquireFairLockAdapter")
        fun acquireFairLockAdapter() = mockk<AcquireFairLockAdapter>()

        @Bean("checkFairLockAdapter")
        fun checkFairLockAdapter() = mockk<CheckFairLockAdapter>()

        @Bean("unlockFairLockAdapter")
        fun unlockFairLockAdapter() = mockk<UnlockFairLockAdapter>()

        @Bean("acquireLockAdapter")
        fun acquireLockAdapter() = mockk<AcquireLockAdapter>()

        @Bean("checkLockAdapter")
        fun checkLockAdapter() = mockk<CheckLockAdapter>()

        @Bean("unlockLockAdapter")
        fun unlockLockAdapter() = mockk<UnlockLockAdapter>()

        @Bean
        fun acquireNamedLockAdapter() = mockk<AcquireNamedLockAdapter>()

        @Bean
        fun unlockNamedLockAdapter() = mockk<UnlockNamedLockAdapter>()

        @Bean
        fun platformTransactionManager() = mockk<PlatformTransactionManager>(relaxed = true)

        @Suppress("LongParameterList")
        @Bean
        fun distributedLockAspect(
            @Qualifier("acquireFairLockAdapter") acquireFairLockAdapter: AcquireLockTemplate,
            @Qualifier("checkFairLockAdapter") checkFairLockAdapter: CheckLockTemplate,
            @Qualifier("unlockFairLockAdapter") unlockFairLockAdapter: UnlockLockTemplate,
            @Qualifier("acquireLockAdapter") acquireLockAdapter: AcquireLockTemplate,
            @Qualifier("checkLockAdapter") checkLockAdapter: CheckLockTemplate,
            @Qualifier("unlockLockAdapter") unlockLockAdapter: UnlockLockTemplate,
            spelParser: SpelParser,
            acquireNamedLockAdapter: AcquireNamedLockAdapter,
            unlockNamedLockAdapter: UnlockNamedLockAdapter,
            platformTransactionManager: PlatformTransactionManager,
        ) = DistributedLockAspect(
            FairLockRedisCoordinator(
                acquireFairLockAdapter,
                checkFairLockAdapter,
                unlockFairLockAdapter,
            ),
            GeneralLockRedisCoordinator(
                acquireLockAdapter,
                checkLockAdapter,
                unlockLockAdapter,
            ),
            NamedLockCoordinator(acquireNamedLockAdapter, unlockNamedLockAdapter),
            spelParser,
            platformTransactionManager,
        )

        // 정원/수명을 들고 있는 쪽이 AdmitSlotService로 옮겨 갔다. 여기서는 실물을 그대로
        // 끼워 넣어, 이 테스트의 관심사(워커에 걸린 분산 락)만 남긴다.
        @Bean
        fun admitSlotUseCase(admitWaitingTickets: AdmitWaitingTickets): AdmitSlotUseCase =
            AdmitSlotService(admitWaitingTickets, CAPACITY, TIME_TO_LIVE_SECONDS)

        @Bean
        fun admitWaitingQueueService(
            loadWaitingQueueSlots: LoadWaitingQueueSlots,
            admitSlot: AdmitSlotUseCase,
        ): AdmitWaitingQueueService = AdmitWaitingQueueService(loadWaitingQueueSlots, admitSlot)
    }

    private fun queueSlot() =
        WaitingQueueSlot(
            restaurantId = UuidGenerator.generate(),
            date = LocalDate.of(2026, 8, 26),
            startTime = LocalTime.of(11, 0),
        )

    @DisplayName("다른 인스턴스가 이미 워커 락을 쥐고 있을 때")
    @Nested
    inner class `Another instance holds the worker lock` {
        @DisplayName("입장 허용 워커가 한 사이클을 돌면")
        @Nested
        inner class `When worker runs once` {
            @DisplayName("승격 로직을 아예 실행하지 않고 즉시 튕겨 나온다")
            @Test
            fun `skip the whole cycle`() {
                every { acquireLockAdapter.tryLock(any(), any(), any()) } returns false
                every { checkLockAdapter.isHeldByCurrentThread(any()) } returns false

                shouldThrow<TooManyRequestHasBeenComeSimultaneouslyException> {
                    admitWaitingQueueUseCase.execute()
                }

                verify(exactly = 0) {
                    loadWaitingQueueSlots.query()
                    admitWaitingTickets.admit(any())
                }
            }
        }
    }

    @DisplayName("워커 락을 잡은 인스턴스일 때")
    @Nested
    inner class `This instance holds the worker lock` {
        @DisplayName("입장 허용 워커가 한 사이클을 돌면")
        @Nested
        inner class `When worker runs once` {
            @DisplayName("승격을 수행하고 끝난 뒤 락을 반납한다")
            @Test
            fun `run the cycle and release the lock`() {
                every { acquireLockAdapter.tryLock(any(), any(), any()) } returns true
                every { checkLockAdapter.isHeldByCurrentThread(any()) } returns true
                every { unlockLockAdapter.unlock(any()) } just Runs
                every { loadWaitingQueueSlots.query() } returns listOf(queueSlot())
                every { admitWaitingTickets.admit(any()) } returns 1

                admitWaitingQueueUseCase.execute() shouldBe 1

                verify(exactly = 1) {
                    loadWaitingQueueSlots.query()
                    unlockLockAdapter.unlock(any())
                }
            }
        }
    }
}
