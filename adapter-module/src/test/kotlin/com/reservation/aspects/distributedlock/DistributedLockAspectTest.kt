package com.reservation.aspects.distributedlock

import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.giveMe
import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.aspects.distributedlock.DistributedLockAspectTest.InitializeMockContext
import com.reservation.config.aspect.DistributedLockAspect
import com.reservation.config.aspect.SpelParser
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.redis.redisson.lock.AcquireLockTemplate
import com.reservation.redis.redisson.lock.CheckLockTemplate
import com.reservation.redis.redisson.lock.UnlockLockTemplate
import com.reservation.redis.redisson.lock.fair.template.adapter.AcquireFairLockAdapter
import com.reservation.redis.redisson.lock.fair.template.adapter.CheckFairLockAdapter
import com.reservation.redis.redisson.lock.fair.template.adapter.UnlockFairLockAdapter
import com.reservation.redis.redisson.lock.general.adapter.AcquireLockAdapter
import com.reservation.redis.redisson.lock.general.adapter.CheckLockAdapter
import com.reservation.redis.redisson.lock.general.adapter.UnlockLockAdapter
import com.reservation.timetable.TimeTable
import com.reservation.timetable.event.TimeTableOccupiedDomainEvent
import com.reservation.timetable.exceptions.TooManyRequestHasBeenComeSimultaneouslyException
import com.reservation.timetable.port.input.CreateTimeTableOccupancyUseCase
import com.reservation.timetable.port.input.command.request.CreateTimeTableOccupancyCommand
import com.reservation.timetable.port.output.AcquireTimeTableSemaphore
import com.reservation.timetable.port.output.CreateTimeTableOccupancy
import com.reservation.timetable.port.output.DelegateReservation
import com.reservation.timetable.port.output.LoadBookableTimeTables
import com.reservation.timetable.port.output.ReleaseSemaphore
import com.reservation.timetable.service.CreateTimeTableOccupancyDomainService
import com.reservation.timetable.service.CreateTimeTableOccupiedDomainEventService
import com.reservation.timetable.snapshot.TimeTableSnapshot
import com.reservation.timetable.snapshot.TimetableOccupancySnapShot
import com.reservation.timetable.usecase.CreateTimeTableOccupancyService
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(value = [SpringExtension::class])
@ContextConfiguration(classes = [InitializeMockContext::class])
class DistributedLockAspectTest {
    private lateinit var pureMonkey: FixtureMonkey

    @Autowired
    private lateinit var service: CreateTimeTableOccupancyUseCase

    @Autowired
    private lateinit var acquireTimeTableSemaphore: AcquireTimeTableSemaphore

    @Autowired
    private lateinit var releaseSemaphore: ReleaseSemaphore

    @Autowired
    private lateinit var loadBookableTimeTables: LoadBookableTimeTables

    @Autowired
    private lateinit var createTimeTableOccupancy: CreateTimeTableOccupancy

    @Autowired
    private lateinit var createTimeTableOccupancyDomainService:
        CreateTimeTableOccupancyDomainService

    @Autowired
    private lateinit var createTimeTableOccupiedDomainEventService:
        CreateTimeTableOccupiedDomainEventService

    @Autowired
    private lateinit var delegateReservation: DelegateReservation

    @Autowired
    @Qualifier(value = "acquireFairLockAdapter")
    private lateinit var acquireFairLockAdapter: AcquireLockTemplate

    @Autowired
    @Qualifier(value = "checkFairLockAdapter")
    private lateinit var checkFairLockAdapter: CheckLockTemplate

    @Autowired
    @Qualifier(value = "unlockFairLockAdapter")
    private lateinit var unlockFairLockAdapter: UnlockLockTemplate

    @BeforeEach
    fun init() {
        pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

        // 모든 Mock 상태 초기화
        clearAllMocks()
    }

    @TestConfiguration
    @EnableAspectJAutoProxy
    class InitializeMockContext {
        @Bean
        fun spelParser() = SpelParser()

        @Bean
        fun acquireTimeTableSemaphore() = mockk<AcquireTimeTableSemaphore>()

        @Bean
        fun releaseSemaphore() = mockk<ReleaseSemaphore>()

        @Bean
        fun loadBookableTimeTables() = mockk<LoadBookableTimeTables>()

        @Bean
        fun createTimeTableOccupancy() = mockk<CreateTimeTableOccupancy>()

        @Bean
        fun createTimeTableOccupancyDomainService() = mockk<CreateTimeTableOccupancyDomainService>()

        @Bean
        fun createTimeTableOccupiedDomainEventService() =
            mockk<CreateTimeTableOccupiedDomainEventService>()

        @Bean
        fun delegateReservation() = mockk<DelegateReservation>()

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

        @Suppress("LongParameterList")
        @Bean
        fun distributedLockAspect(
            acquireFairLockAdapter: AcquireLockTemplate,
            checkFairLockAdapter: CheckLockTemplate,
            unlockFairLockAdapter: UnlockLockTemplate,
            acquireLockAdapter: AcquireLockTemplate,
            checkLockAdapter: CheckLockTemplate,
            unlockLockAdapter: UnlockLockTemplate,
            spelParser: SpelParser,
        ) = DistributedLockAspect(
            acquireFairLockAdapter,
            checkFairLockAdapter,
            unlockFairLockAdapter,
            acquireLockAdapter,
            checkLockAdapter,
            unlockLockAdapter,
            spelParser,
        )

        @Suppress("LongParameterList")
        @Bean
        fun createTimeTableOccupancyService(
            acquireTimeTableSemaphore: AcquireTimeTableSemaphore,
            releaseSemaphore: ReleaseSemaphore,
            loadBookableTimeTables: LoadBookableTimeTables,
            createTimeTableOccupancy: CreateTimeTableOccupancy,
            createTimeTableOccupancyDomainService: CreateTimeTableOccupancyDomainService,
            createTimeTableOccupiedDomainEventService: CreateTimeTableOccupiedDomainEventService,
            delegateReservation: DelegateReservation,
        ): CreateTimeTableOccupancyService {
            return CreateTimeTableOccupancyService(
                acquireTimeTableSemaphore,
                releaseSemaphore,
                loadBookableTimeTables,
                createTimeTableOccupancy,
                createTimeTableOccupancyDomainService,
                createTimeTableOccupiedDomainEventService,
                delegateReservation,
            )
        }
    }

    @Test
    fun contextLoads() {
        val targets =
            listOf(
                AopUtils.isAopProxy(service),
                AopUtils.isJdkDynamicProxy(service),
                AopUtils.isCglibProxy(service),
            )

        assertSoftly {
            assertThat(targets).anyMatch { it }
        }
    }

    // Scenario 1: Fair Lock 획득 타임아웃
    // Given: 동일한 시간대에 대해 많은 요청이 대기 중이고 Fair Lock 타임아웃이 발생했을 때
    // When: 예약 생성을 요청하면
    // Then: TooManyRequestHasBeenComeSimultaneouslyException이 발생한다
    @DisplayName("동일한 시간대에 대해 많은 요청이 대기 중이고 Fair Lock 타임아웃이 발생했을 때")
    @Nested
    inner class `Request income when fair lock is over` {
        @DisplayName("예약 생성을 요청하면")
        @Nested
        inner class `When request booking` {
            @DisplayName("TooManyRequestHasBeenComeSimultaneouslyException이 발생한다")
            @Test
            fun `throw TooManyRequestHasBeenComeSimultaneouslyException`() {
                val command = pureMonkey.giveMeOne<CreateTimeTableOccupancyCommand>()

                every { acquireFairLockAdapter.tryLock(any(), any(), any()) } returns false
                every { checkFairLockAdapter.isHeldByCurrentThread(any()) } returns false

                shouldThrow<TooManyRequestHasBeenComeSimultaneouslyException> {
                    service.execute(
                        command,
                    )
                }

                verify(exactly = 0) {
                    acquireTimeTableSemaphore.tryAcquire(any(), any(), any())
                    releaseSemaphore.release(any())
                    loadBookableTimeTables.query(any())
                    createTimeTableOccupancy.command(any())
                    createTimeTableOccupancyDomainService.create(any(), any())
                }
            }
        }
    }

    // Scenario 2: 정상적인 예약 생성 - 단일 테이블
    // Given: 유효한 사용자와 예약 가능한 테이블이 1개 있을 때
    // When: 예약 생성을 요청하면
    // Then: 예약이 성공적으로 생성되고 true를 반환한다
    @DisplayName("유효한 사용자와 예약 가능한 테이블이 1개 있을 때")
    @Nested
    inner class `Request income and booking success` {
        @DisplayName("예약 생성을 요청하면")
        @Nested
        inner class `When request booking` {
            @DisplayName("예약이 성공적으로 생성되고 true를 반환한다")
            @Test
            fun `booking success and return true`() {
                val event = pureMonkey.giveMeOne<TimeTableOccupiedDomainEvent>()
                val occupancyId = UuidGenerator.generate()
                val command = pureMonkey.giveMeOne<CreateTimeTableOccupancyCommand>()
                val list = pureMonkey.giveMe<TimeTable>(4)
                val occupancySnapshot = pureMonkey.giveMeOne<TimetableOccupancySnapShot>()
                val snapshot =
                    pureMonkey.giveMeBuilder<TimeTableSnapshot>()
                        .set("id", UuidGenerator.generate())
                        .set("timetableOccupancy", occupancySnapshot)
                        .sample()
                every {
                    acquireFairLockAdapter.tryLock(any(), any(), any())
                } returns true

                every {
                    loadBookableTimeTables.query(any())
                } returns list

                every {
                    acquireTimeTableSemaphore.tryAcquire(any(), any(), any())
                } returns true

                every {
                    createTimeTableOccupancyDomainService.create(any(), any())
                } returns snapshot

                every {
                    createTimeTableOccupancy.command(any())
                } returns occupancyId

                every {
                    createTimeTableOccupiedDomainEventService.create(any(), any())
                } returns event

                every {
                    delegateReservation.command(any<TimeTableOccupiedDomainEvent>())
                } just Runs

                every {
                    checkFairLockAdapter.isHeldByCurrentThread(any())
                } returns true

                every {
                    unlockFairLockAdapter.unlock(any())
                } just Runs

                val result = service.execute(command)

                result shouldBe true
                verify(exactly = 1) {
                    acquireTimeTableSemaphore.tryAcquire(any(), any(), any())
                    loadBookableTimeTables.query(any())
                    createTimeTableOccupancy.command(any())
                    createTimeTableOccupancyDomainService.create(any(), any())
                    createTimeTableOccupiedDomainEventService.create(any(), any())
                    delegateReservation.command(any<TimeTableOccupiedDomainEvent>())
                }
                verify(exactly = 0) { releaseSemaphore.release(any()) }
            }
        }
    }
}
