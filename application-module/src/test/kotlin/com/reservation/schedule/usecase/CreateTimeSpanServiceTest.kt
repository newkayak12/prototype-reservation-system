package com.reservation.schedule.usecase

import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.schedule.exceptions.InvalidTimeSpanElementException
import com.reservation.schedule.port.input.command.request.CreateTimeSpanCommand
import com.reservation.schedule.port.output.ChangeSchedule
import com.reservation.schedule.port.output.LoadSchedule
import com.reservation.schedule.port.output.LoadSchedule.LoadScheduleResult
import com.reservation.schedule.service.CreateTimeSpanDomainService
import com.reservation.schedule.snapshot.ScheduleSnapshot
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import net.jqwik.api.Arbitraries
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.dao.DataIntegrityViolationException

@ExtendWith(MockKExtension::class)
@DisplayName("시간을 생성할 때, ")
class CreateTimeSpanServiceTest {
    @MockK
    private lateinit var createTimeSpanDomainService: CreateTimeSpanDomainService

    @MockK
    private lateinit var loadSchedule: LoadSchedule

    @MockK
    private lateinit var changeSchedule: ChangeSchedule

    @InjectMockKs
    private lateinit var createTimeSpanService: CreateTimeSpanService

    // 저장된 Schedule이 없는 경우에 NoSuchPersistedElementException이 발생한다
    @DisplayName("저장된 Schedule이 없는 경우에 ")
    @Nested
    inner class NoSchedule {
        @DisplayName("NoSuchPersistedElementException이 발생한다")
        @Test
        fun throwNoSuchPersistedElementException() {
            val fixtureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val command = fixtureMonkey.giveMeOne<CreateTimeSpanCommand>()

            every {
                loadSchedule.query(any())
            } throws NoSuchPersistedElementException()

            assertThrows<NoSuchPersistedElementException> {
                createTimeSpanService.execute(command)
            }

            verify(exactly = 0) {
                createTimeSpanDomainService.create(any(), any())
                changeSchedule.command(any())
            }
            verify(exactly = 1) {
                loadSchedule.query(any())
            }
        }
    }

    // TimeSpan 생성에 문제가 생긴 경우에 InvalidateTimeSpanElementException이 발생한다
    @DisplayName("TimeSpan 생성에 문제가 생겨서")
    @Nested
    inner class CreateTimeSpanFailure {
        @DisplayName("InvalidateTimeSpanElementException이 발생한다.")
        @Test
        fun throwInvalidateTimeSpanElementException() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val createTimeSpanCommand = pureMonkey.giveMeOne<CreateTimeSpanCommand>()
            val loadScheduleResult = pureMonkey.giveMeOne<LoadScheduleResult>()

            every {
                loadSchedule.query(any())
            } returns loadScheduleResult

            every {
                createTimeSpanDomainService.create(any(), any())
            } throws InvalidTimeSpanElementException(Arbitraries.strings().sample())

            assertThrows<InvalidTimeSpanElementException> {
                createTimeSpanService.execute(createTimeSpanCommand)
            }

            verify(exactly = 1) { loadSchedule.query(any()) }
            verify(exactly = 1) { createTimeSpanDomainService.create(any(), any()) }
            verify(exactly = 0) { changeSchedule.command(any()) }
        }
    }

    // TimeSpan 생성에 성공했지만 스케줄 저장에 실패했을 때 DataIntegrityViolationException이 발생한다
    @DisplayName("TimeSpan 생성에 성공하고 저장을 시도한다.")
    @Nested
    inner class PersistTimeSpan {
        @DisplayName("저장에 실패하고 DataIntegrityViolationException이 발생한다")
        @Test
        fun throwDataIntegrityViolationException() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val createTimeSpanCommand = pureMonkey.giveMeOne<CreateTimeSpanCommand>()
            val schedule = pureMonkey.giveMeOne<ScheduleSnapshot>()
            val loadScheduleResult = pureMonkey.giveMeOne<LoadScheduleResult>()

            every {
                loadSchedule.query(any())
            } returns loadScheduleResult

            every {
                createTimeSpanDomainService.create(any(), any())
            } returns schedule

            every {
                changeSchedule.command(any())
            } throws DataIntegrityViolationException(Arbitraries.strings().sample())

            assertThrows<DataIntegrityViolationException> {
                createTimeSpanService.execute(createTimeSpanCommand)
            }

            verify(exactly = 1) { loadSchedule.query(any()) }
            verify(exactly = 1) { createTimeSpanDomainService.create(any(), any()) }
            verify(exactly = 1) { changeSchedule.command(any()) }
        }
    }

    // TimeSpan 생성에 성공하고 스케줄 저장에 성공했을 때 true를 반환한다

    @DisplayName("TimeSpan 생성에 성공하고")
    @Nested
    inner class CreateTimeSpanSuccessfully {
        @DisplayName("스케줄 저장에 성공했을 때 true를 반환한다")
        @Test
        fun success() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val createTimeSpanCommand = pureMonkey.giveMeOne<CreateTimeSpanCommand>()
            val schedule = pureMonkey.giveMeOne<ScheduleSnapshot>()
            val loadScheduleResult = pureMonkey.giveMeOne<LoadScheduleResult>()

            every {
                loadSchedule.query(any())
            } returns loadScheduleResult

            every {
                createTimeSpanDomainService.create(any(), any())
            } returns schedule

            every {
                changeSchedule.command(any())
            } returns true

            val result = createTimeSpanService.execute(createTimeSpanCommand)

            assertTrue(result)

            verify(exactly = 1) { loadSchedule.query(any()) }
            verify(exactly = 1) { createTimeSpanDomainService.create(any(), any()) }
            verify(exactly = 1) { changeSchedule.command(any()) }
        }
    }
}
