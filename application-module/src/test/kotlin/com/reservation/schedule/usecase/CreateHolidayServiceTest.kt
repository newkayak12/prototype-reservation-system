package com.reservation.schedule.usecase

import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.schedule.exceptions.InvalidateHolidayElementException
import com.reservation.schedule.port.input.command.request.CreateHolidayCommand
import com.reservation.schedule.port.output.ChangeSchedule
import com.reservation.schedule.port.output.LoadSchedule
import com.reservation.schedule.port.output.LoadSchedule.LoadScheduleResult
import com.reservation.schedule.service.CreateHolidayDomainService
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
@DisplayName("휴일을 생성할 때, ")
class CreateHolidayServiceTest {

    @MockK
    lateinit var createHolidayDomainService: CreateHolidayDomainService

    @MockK
    lateinit var loadSchedule: LoadSchedule

    @MockK
    lateinit var changeSchedule: ChangeSchedule

    @InjectMockKs
    lateinit var service: CreateHolidayService


    @DisplayName("저장된 Schedule이 없는 경우에")
    @Nested
    inner class `No Schedule Exists` {
        private val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

        @DisplayName("NoSuchPersistedElementException가 발생한다.")
        @Test
        fun `Throw NoSuchPersistedElementException`() {
            val command = pureMonkey.giveMeOne<CreateHolidayCommand>()

            every {
                loadSchedule.query(any())
            } throws NoSuchPersistedElementException()

            assertThrows<NoSuchPersistedElementException> {
                service.execute(command)
            }

            verify(exactly = 1) {
                loadSchedule.query(any())
            }
            verify(exactly = 0) {
                createHolidayDomainService.create(any(), any())
            }
            verify(exactly = 0){
                changeSchedule.command(any())
            }

        }
    }

    @DisplayName("휴일 생성에 문제가 생긴 경우에")
    @Nested
    inner class `Throw Exception When Create Holiday` {
        private val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

        @DisplayName("InvalidateHolidayElementException 발생한다.")
        @Test
        fun `Throw InvalidateHolidayElementException`() {
            val command = pureMonkey.giveMeOne<CreateHolidayCommand>()
            val queryResult = pureMonkey.giveMeOne<LoadScheduleResult>()

            every {
                loadSchedule.query(any())
            } returns queryResult

            every {
                createHolidayDomainService.create(any(), any())
            } throws InvalidateHolidayElementException(Arbitraries.strings().sample())

            assertThrows<InvalidateHolidayElementException> {
                service.execute(command)
            }

            verify(exactly = 1) {
                loadSchedule.query(any())
            }
            verify(exactly = 1) {
                createHolidayDomainService.create(any(), any())
            }
            verify(exactly = 0){
                changeSchedule.command(any())
            }
        }
    }

    @DisplayName("휴일 생성에 대해서 스케쥴 저장에 실패했을 때")
    @Nested
    inner class `Fail to save Schedule` {
        private val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

        @DisplayName("InvalidateHolidayElementException 발생한다.")
        @Test
        fun `Throw InvalidateHolidayElementException`() {
            val command = pureMonkey.giveMeOne<CreateHolidayCommand>()
            val queryResult = pureMonkey.giveMeOne<LoadScheduleResult>()
            val snapshot = pureMonkey.giveMeOne<ScheduleSnapshot>()

            every {
                loadSchedule.query(any())
            } returns queryResult

            every {
                createHolidayDomainService.create(any(), any())
            } returns snapshot

            every {
                changeSchedule.command(any())
            } throws DataIntegrityViolationException(Arbitraries.strings().sample())

            assertThrows<DataIntegrityViolationException> {
                service.execute(command)
            }

            verify(exactly = 1) {
                loadSchedule.query(any())
            }
            verify(exactly = 1) {
                createHolidayDomainService.create(any(), any())
            }
            verify(exactly = 1){
                changeSchedule.command(any())
            }
        }
    }

    @DisplayName("휴일 생성에 성공하고 스케쥴 저장에 성공했을 때")
    @Nested
    inner class `Success to save schdule` {
        private val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

        @DisplayName("휴일 등록에 성공한다.")
        @Test
        fun `success`() {
            val command = pureMonkey.giveMeOne<CreateHolidayCommand>()
            val queryResult = pureMonkey.giveMeOne<LoadScheduleResult>()
            val snapshot = pureMonkey.giveMeOne<ScheduleSnapshot>()

            every {
                loadSchedule.query(any())
            } returns queryResult

            every {
                createHolidayDomainService.create(any(), any())
            } returns snapshot

            every {
                changeSchedule.command(any())
            } returns true

            val result = service.execute(command)

            assertTrue(result)

            verify(exactly = 1) {
                loadSchedule.query(any())
            }
            verify(exactly = 1) {
                createHolidayDomainService.create(any(), any())
            }
            verify(exactly = 1){
                changeSchedule.command(any())
            }
        }
    }

}
