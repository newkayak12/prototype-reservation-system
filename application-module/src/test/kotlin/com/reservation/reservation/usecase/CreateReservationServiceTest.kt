package com.reservation.reservation.usecase

import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.reservation.exceptions.ReservationRestaurantIdException
import com.reservation.reservation.exceptions.ReservationTimeTableIdException
import com.reservation.reservation.exceptions.ReservationTimeTableOccupancyIdException
import com.reservation.reservation.exceptions.ReservationUserIdException
import com.reservation.reservation.port.input.command.CreateReservationCommand
import com.reservation.reservation.port.output.CreateReservation
import com.reservation.reservation.service.CreateReservationDomainService
import com.reservation.reservation.snapshot.ReservationSnapshot
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import net.jqwik.api.Arbitraries
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
@DisplayName("예약을 생성하기 위해서")
class CreateReservationServiceTest {
    private lateinit var pureMonkey: FixtureMonkey

    @BeforeEach
    fun setUp() {
        pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
    }

    @Nested
    @DisplayName("정상적인 파라미터를 투입하여")
    inner class CreationSuccess {
        @MockK
        private lateinit var createReservationDomainService: CreateReservationDomainService

        @MockK
        private lateinit var createReservation: CreateReservation

        @InjectMockKs
        private lateinit var createReservationService: CreateReservationService

        @DisplayName("생성을 진행하면 성공한다.")
        @Test
        fun returnTrue() {
            val command = pureMonkey.giveMeOne<CreateReservationCommand>()
            val snapshot = pureMonkey.giveMeOne<ReservationSnapshot>()

            every {
                createReservationDomainService.createReservation(any())
            } returns snapshot

            every {
                createReservation.command(any())
            } returns true

            val result = createReservationService.execute(command)

            result shouldBe true
            verify(exactly = 1) {
                createReservationDomainService.createReservation(any())
                createReservation.command(any())
            }
        }
    }

    @Nested
    @DisplayName("비정상적인 파라미터를 투입하여")
    inner class CreationFailed {
        @MockK
        private lateinit var createReservationDomainService: CreateReservationDomainService

        @MockK
        private lateinit var createReservation: CreateReservation

        @InjectMockKs
        private lateinit var createReservationService: CreateReservationService

        @DisplayName("생성 중 userId가 validation에 걸려 ReservationUserIdException이 발생한다.")
        @Test
        fun throwReservationUserIdException() {
            val command = pureMonkey.giveMeOne<CreateReservationCommand>()
            val exceptionMessage = Arbitraries.strings().sample()

            every {
                createReservationDomainService.createReservation(any())
            } throws ReservationUserIdException(exceptionMessage)

            assertThrows<ReservationUserIdException> { createReservationService.execute(command) }

            verify(exactly = 1) {
                createReservationDomainService.createReservation(any())
            }
            verify(exactly = 0) {
                createReservation.command(any())
            }
        }

        @DisplayName(
            "생성 중 restaurantId가 validation에 걸려" +
                " ReservationRestaurantIdException이 발생한다.",
        )
        @Test
        fun throwReservationRestaurantIdException() {
            val command = pureMonkey.giveMeOne<CreateReservationCommand>()
            val exceptionMessage = Arbitraries.strings().sample()

            every {
                createReservationDomainService.createReservation(any())
            } throws ReservationRestaurantIdException(exceptionMessage)

            assertThrows<ReservationRestaurantIdException> {
                createReservationService.execute(
                    command,
                )
            }

            verify(exactly = 1) {
                createReservationDomainService.createReservation(any())
            }
            verify(exactly = 0) {
                createReservation.command(any())
            }
        }

        @DisplayName("생성 중 timeTableId가 validation에 걸려 ReservationTimeTableIdException이 발생한다.")
        @Test
        fun throwReservationTimeTableIdException() {
            val command = pureMonkey.giveMeOne<CreateReservationCommand>()
            val exceptionMessage = Arbitraries.strings().sample()

            every {
                createReservationDomainService.createReservation(any())
            } throws ReservationTimeTableIdException(exceptionMessage)
            listOf(
                ReservationRestaurantIdException(exceptionMessage),
                ReservationTimeTableIdException(exceptionMessage),
                ReservationTimeTableOccupancyIdException(exceptionMessage),
            )

            assertThrows<ReservationTimeTableIdException> {
                createReservationService.execute(command)
            }

            verify(exactly = 1) {
                createReservationDomainService.createReservation(any())
            }
            verify(exactly = 0) {
                createReservation.command(any())
            }
        }

        @DisplayName(
            "생성 중 TimeTableOccupancyId가 validation에 걸려" +
                " ReservationTimeTableOccupancyIdException이 발생한다.",
        )
        @Test
        fun throwReservationTimeTableOccupancyIdException() {
            val command = pureMonkey.giveMeOne<CreateReservationCommand>()
            val exceptionMessage = Arbitraries.strings().sample()

            every {
                createReservationDomainService.createReservation(any())
            } throws ReservationTimeTableOccupancyIdException(exceptionMessage)

            assertThrows<ReservationTimeTableOccupancyIdException> {
                createReservationService.execute(command)
            }

            verify(exactly = 1) {
                createReservationDomainService.createReservation(any())
            }
            verify(exactly = 0) {
                createReservation.command(any())
            }
        }
    }
}
