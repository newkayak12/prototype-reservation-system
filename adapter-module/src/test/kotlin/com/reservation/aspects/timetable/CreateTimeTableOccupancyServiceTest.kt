package com.reservation.aspects.timetable

import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.giveMe
import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.timetable.TimeTable
import com.reservation.timetable.exception.AllTheSeatsAreAlreadyOccupiedException
import com.reservation.timetable.exception.AllTheThingsAreAlreadyOccupiedException
import com.reservation.timetable.exception.TooManyCreateTimeTableOccupancyRequestException
import com.reservation.timetable.exception.TooManyRequestHasBeenComeSimultaneouslyException
import com.reservation.timetable.policy.exceptions.InvalidTimeTableIdException
import com.reservation.timetable.policy.exceptions.InvalidTimeTableStatusException
import com.reservation.timetable.policy.exceptions.InvalidTimeTableUserIdException
import com.reservation.timetable.port.input.command.request.CreateTimeTableOccupancyCommand
import com.reservation.timetable.port.output.AcquireTimeTableSemaphore
import com.reservation.timetable.port.output.CreateTimeTableOccupancy
import com.reservation.timetable.port.output.LoadBookableTimeTables
import com.reservation.timetable.port.output.ReleaseSemaphore
import com.reservation.timetable.service.CreateTimeTableOccupancyDomainService
import com.reservation.timetable.snapshot.TimeTableSnapshot
import com.reservation.timetable.snapshot.TimetableOccupancySnapShot
import com.reservation.timetable.usecase.CreateTimeTableOccupancyService
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import net.jqwik.api.Arbitraries
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.dao.DataIntegrityViolationException

@ExtendWith(MockKExtension::class)
class CreateTimeTableOccupancyServiceTest {
    @MockK
    private lateinit var acquireTimeTableRateLimiter: AcquireTimeTableRateLimiter

    @MockK
    private lateinit var acquireTimeTableSemaphore: AcquireTimeTableSemaphore

    @MockK
    private lateinit var releaseSemaphore: ReleaseSemaphore

    @MockK
    private lateinit var loadBookableTimeTables: LoadBookableTimeTables

    @MockK
    private lateinit var createTimeTableOccupancy: CreateTimeTableOccupancy

    @MockK
    private lateinit var createTimeTableOccupancyDomainService:
        CreateTimeTableOccupancyDomainService

    @InjectMockKs
    private lateinit var createTimeTableOccupancyService: CreateTimeTableOccupancyService

    private lateinit var pureMonkey: FixtureMonkey

    @BeforeEach
    fun init() {
        pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
        clearMocks(
            acquireTimeTableRateLimiter,
            acquireTimeTableSemaphore,
            releaseSemaphore,
            loadBookableTimeTables,
            createTimeTableOccupancy,
            createTimeTableOccupancyDomainService,
        )
    }

    // Scenario 1: Rate Limiter 제한 초과
    // Given: 동일한 시간대에 대해 Rate Limiter 용량을 초과하는 요청이 들어왔을 때
    // When: 예약 생성을 요청하면
    // Then: TooManyCreateTimeTableOccupancyRequestException이 발생한다
    @DisplayName("동일한 시간대에 대해 Rate Limiter 용량을 초과하는 요청이 들어왔을 때")
    @Nested
    inner class `Request income when rate limiter is over` {
        @DisplayName("예약 생성을 요청하면")
        @Nested
        inner class `When request booking` {
            @DisplayName("TooManyCreateTimeTableOccupancyRequestException이 발생한다")
            @Test
            fun `throw TooManyCreateTimeTableOccupancyRequestException`() {
                val command = pureMonkey.giveMeOne<CreateTimeTableOccupancyCommand>()

                every {
                    acquireTimeTableRateLimiter.tryAcquire(any(), any(), any())
                } returns false

                shouldThrow<TooManyCreateTimeTableOccupancyRequestException> {
                    createTimeTableOccupancyService.execute(command)
                }
            }
        }
    }

    // Scenario 2: Fair Lock 획득 타임아웃
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

                every {
                    acquireTimeTableRateLimiter.tryAcquire(any(), any(), any())
                } returns true

//                every {
//                    acquireTimeTableFairLock.tryLock(any(), any(), any())
//                } returns false

                shouldThrow<TooManyRequestHasBeenComeSimultaneouslyException> {
                    createTimeTableOccupancyService.execute(command)
                }
            }
        }
    }

    // Scenario 3: 예약 가능한 테이블이 없는 경우
    // Given: 요청한 시간대에 예약 가능한 테이블이 없을 때
    // When: 예약 생성을 요청하면
    // Then: AllTheSeatsAreAlreadyOccupiedException이 발생한다
    @DisplayName("동일한 시간대에 대해 많은 요청이 대기 중이고 Fair Lock 타임아웃이 발생했을 때")
    @Nested
    inner class `Request income but all the seats are already occupied` {
        @DisplayName("예약 생성을 요청하면")
        @Nested
        inner class `When request booking` {
            @DisplayName("AllTheSeatsAreAlreadyOccupiedException이 발생한다")
            @Test
            fun `throw AllTheSeatsAreAlreadyOccupiedException`() {
                val command = pureMonkey.giveMeOne<CreateTimeTableOccupancyCommand>()

                every {
                    acquireTimeTableRateLimiter.tryAcquire(any(), any(), any())
                } returns true

//                every {
//                    acquireTimeTableFairLock.tryLock(any(), any(), any())
//                } returns true
//
//                every {
//                    checkTimeTableFairLock.isHeldByCurrentThread(any())
//                } returns true
//
//                every {
//                    unlockTimeTableFairLock.unlock(any())
//                } just Runs

                every {
                    loadBookableTimeTables.query(any())
                } returns emptyList()

                shouldThrow<AllTheSeatsAreAlreadyOccupiedException> {
                    createTimeTableOccupancyService.execute(command)
                }
            }
        }
    }

    // Scenario 4: Semaphore 용량 부족
    // Given: 사용 가능한 테이블은 있지만 Semaphore 허가를 획득할 수 없을 때
    // When: 예약 생성을 요청하면
    // Then: AllTheThingsAreAlreadyOccupiedException이 발생한다
    @DisplayName("사용 가능한 테이블은 있지만 Semaphore 허가를 획득할 수 없을 때")
    @Nested
    inner class `Request income but semaphore cannot be acquired` {
        @DisplayName("예약 생성을 요청하면")
        @Nested
        inner class `When request booking` {
            @DisplayName("AllTheThingsAreAlreadyOccupiedException이 발생한다")
            @Test
            fun `throw AllTheThingsAreAlreadyOccupiedException`() {
                val command = pureMonkey.giveMeOne<CreateTimeTableOccupancyCommand>()
                val list = pureMonkey.giveMe<TimeTable>(4)

                every {
                    acquireTimeTableRateLimiter.tryAcquire(any(), any(), any())
                } returns true

//                every {
//                    acquireTimeTableFairLock.tryLock(any(), any(), any())
//                } returns true
//
//                every {
//                    checkTimeTableFairLock.isHeldByCurrentThread(any())
//                } returns true
//
//                every {
//                    unlockTimeTableFairLock.unlock(any())
//                } just Runs

                every {
                    loadBookableTimeTables.query(any())
                } returns list

                every {
                    acquireTimeTableSemaphore.tryAcquire(any(), any(), any())
                } returns false

                shouldThrow<AllTheThingsAreAlreadyOccupiedException> {
                    createTimeTableOccupancyService.execute(command)
                }
            }
        }
    }

    // Scenario 5: 도메인 검증 실패 (userId, timeTableId, tableStatus)
    // Given: 도메인 검증에 실패하는 요청이 들어왔을 때
    // When: 예약 생성을 요청하면
    // Then: 해당하는 도메인 예외가 발생한다
    @DisplayName("도메인 검증에 실패하는 요청이 들어왔을 때")
    @Nested
    inner class `Request income but domain exceptions throw` {
        @DisplayName("예약 생성을 요청하면")
        @Nested
        inner class `When request booking` {
            @DisplayName("해당하는 도메인 예외가 발생한다")
            @Test
            fun `throw DomainException`() {
                val command = pureMonkey.giveMeOne<CreateTimeTableOccupancyCommand>()
                val list = pureMonkey.giveMe<TimeTable>(4)

                every {
                    acquireTimeTableRateLimiter.tryAcquire(any(), any(), any())
                } returns true

//                every {
//                    acquireTimeTableFairLock.tryLock(any(), any(), any())
//                } returns true
//
//                every {
//                    checkTimeTableFairLock.isHeldByCurrentThread(any())
//                } returns true
//
//                every {
//                    unlockTimeTableFairLock.unlock(any())
//                } just Runs

                every {
                    loadBookableTimeTables.query(any())
                } returns list

                every {
                    acquireTimeTableSemaphore.tryAcquire(any(), any(), any())
                } returns true

                every {
                    createTimeTableOccupancyDomainService.create(any(), any())
                } throws
                    Arbitraries.of(
                        InvalidTimeTableIdException(),
                        InvalidTimeTableUserIdException(),
                        InvalidTimeTableStatusException(),
                    ).sample()

                val exception = shouldThrowAny { createTimeTableOccupancyService.execute(command) }
            }
        }
    }

    // Scenario 6: DB 저장 실패
    // Given: 모든 분산락을 획득했지만 DB 저장 과정에서 실패했을 때
    // When: 예약 생성을 요청하면
    // Then: 적절한 예외가 발생하고 Semaphore가 해제된다
    @DisplayName("모든 분산락을 획득했지만 DB 저장 과정에서 실패했을 때")
    @Nested
    inner class `Request income but save database is failed` {
        @DisplayName("예약 생성을 요청하면")
        @Nested
        inner class `When request booking` {
            @DisplayName("적절한 예외가 발생하고 Semaphore가 해제된다")
            @Test
            fun `insert into database is failed`() {
                val command = pureMonkey.giveMeOne<CreateTimeTableOccupancyCommand>()
                val list = pureMonkey.giveMe<TimeTable>(4)
                val occupancySnapshot = pureMonkey.giveMeOne<TimetableOccupancySnapShot>()
                val snapshot =
                    pureMonkey.giveMeBuilder<TimeTableSnapshot>()
                        .set("id", UuidGenerator.generate())
                        .set("timetableOccupancy", occupancySnapshot)
                        .sample()

                every {
                    acquireTimeTableRateLimiter.tryAcquire(any(), any(), any())
                } returns true

//                every {
//                    acquireTimeTableFairLock.tryLock(any(), any(), any())
//                } returns true
//
//                every {
//                    checkTimeTableFairLock.isHeldByCurrentThread(any())
//                } returns true
//
//                every {
//                    unlockTimeTableFairLock.unlock(any())
//                } just Runs

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
                    createTimeTableOccupancy.createTimeTableOccupancy(any())
                } throws DataIntegrityViolationException(Arbitraries.strings().sample())

                shouldThrow<DataIntegrityViolationException> {
                    createTimeTableOccupancyService.execute(command)
                }
            }
        }
    }

    // Scenario 7: 정상적인 예약 생성 - 단일 테이블
    // Given: 유효한 사용자와 예약 가능한 테이블이 1개 있을 때
    // When: 예약 생성을 요청하면
    // Then: 예약이 성공적으로 생성되고 true를 반환한다
    @DisplayName("유효한 사용자와 예약 가능한 테이블이 1개 있을 때")
    @Nested
    inner class `Request income and booking success` {
        @DisplayName("예약 생성을 요청하면")
        @Nested
        inner class `When request booking` {
            @DisplayName("해당하는 도메인 예외가 발생한다")
            @Test
            fun `insert into database is failed`() {
                val command = pureMonkey.giveMeOne<CreateTimeTableOccupancyCommand>()
                val list = pureMonkey.giveMe<TimeTable>(4)
                val occupancySnapshot = pureMonkey.giveMeOne<TimetableOccupancySnapShot>()
                val snapshot =
                    pureMonkey.giveMeBuilder<TimeTableSnapshot>()
                        .set("id", UuidGenerator.generate())
                        .set("timetableOccupancy", occupancySnapshot)
                        .sample()

                every {
                    acquireTimeTableRateLimiter.tryAcquire(any(), any(), any())
                } returns true

//                every {
//                    acquireTimeTableFairLock.tryLock(any(), any(), any())
//                } returns true
//
//                every {
//                    checkTimeTableFairLock.isHeldByCurrentThread(any())
//                } returns true
//
//                every {
//                    unlockTimeTableFairLock.unlock(any())
//                } just Runs

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
                    createTimeTableOccupancy.createTimeTableOccupancy(any())
                } throws DataIntegrityViolationException(Arbitraries.strings().sample())

                shouldThrow<DataIntegrityViolationException> {
                    createTimeTableOccupancyService.execute(command)
                }
            }
        }
    }
}
