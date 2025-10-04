package com.reservation.schedule.usecase

import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.restaurant.exceptions.InvalidateRestaurantElementException
import com.reservation.schedule.port.input.command.request.CreateScheduleCommand
import com.reservation.schedule.port.output.CreateSchedule
import com.reservation.schedule.service.CreateScheduleDomainService
import com.reservation.schedule.snapshot.ScheduleSnapshot
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import net.jqwik.api.Arbitraries
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.dao.DataIntegrityViolationException

@ExtendWith(MockKExtension::class)
@DisplayName("스케쥴을 생성할 때, ")
class CreateScheduleServiceTest {
    @MockK
    lateinit var createSchedule: CreateSchedule

    @MockK
    lateinit var createScheduleDomainService: CreateScheduleDomainService

    @InjectMockKs
    lateinit var createScheduleService: CreateScheduleService

    @DisplayName("restaurant Id가 없는 요청으로 ")
    @Nested
    inner class `empty restaurant id` {
        private lateinit var pureMonkey: FixtureMonkey

        @BeforeEach
        fun setPureMonkey() {
            pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
        }

        @DisplayName("DomainService에서 InvalidateRestaurantElementException이 발생한다.")
        @Test
        fun `throw InvalidateRestaurantElementException`() {
            val command = pureMonkey.giveMeOne<CreateScheduleCommand>()

            every {
                createScheduleDomainService.create(any())
            } throws InvalidateRestaurantElementException(Arbitraries.strings().sample())

            assertThrows<InvalidateRestaurantElementException> {
                createScheduleService.execute(command)
            }

            verify(exactly = 1) { createScheduleDomainService.create(any()) }
            verify(exactly = 0) { createSchedule.command(any()) }
        }
    }

    @DisplayName("restaurant id가 있는 요청으로 ")
    @Nested
    inner class `valid request` {
        private lateinit var pureMonkey: FixtureMonkey

        @BeforeEach
        fun setPureMonkey() {
            pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
        }

        @DisplayName("데이터 저장에 문제가 생겨 DataIntegrityViolationException가 발생한다.")
        @Test
        fun `throw DataIntegrityViolationException`(){
            val command = pureMonkey.giveMeOne<CreateScheduleCommand>()
            val snapshot = pureMonkey.giveMeOne<ScheduleSnapshot>()

            every {
                createScheduleDomainService.create(any())
            } returns snapshot

            every {
                createSchedule.command(any())
            } throws  DataIntegrityViolationException(Arbitraries.strings().sample())

            assertThrows<DataIntegrityViolationException> {
                createScheduleService.execute(command)
            }

            verify(exactly = 1) { createScheduleDomainService.create(any()) }
            verify(exactly = 1) { createSchedule.command(any()) }
        }

        @DisplayName("스케쥴이 생성된다.")
        @Test
        fun `creation succeed`() {
            val command = pureMonkey.giveMeOne<CreateScheduleCommand>()
            val snapshot = pureMonkey.giveMeOne<ScheduleSnapshot>()

            every {
                createScheduleDomainService.create(any())
            } returns snapshot

            every {
                createSchedule.command(any())
            } returns true

            val result = createScheduleService.execute(command)

            assertTrue(result)
            verify(exactly = 1) { createScheduleDomainService.create(any()) }
            verify(exactly = 1) { createSchedule.command(any()) }
        }
    }
}
