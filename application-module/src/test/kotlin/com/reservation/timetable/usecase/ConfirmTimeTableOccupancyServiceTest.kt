package com.reservation.timetable.usecase

import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.timetable.event.TimeTableOccupiedDomainEvent
import com.reservation.timetable.exceptions.NoHoldToConfirmException
import com.reservation.timetable.port.input.command.request.ConfirmTimeTableOccupancyCommand
import com.reservation.timetable.port.output.ConfirmTimeTableOccupancy
import com.reservation.timetable.port.output.ConfirmTimeTableOccupancy.ConfirmInquiry
import com.reservation.timetable.port.output.ConfirmTimeTableOccupancy.ConfirmedOccupancy
import com.reservation.timetable.service.CreateTimeTableOccupiedDomainEventService
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher

/**
 * 확정이 하류 이벤트의 유일한 출발점인지 못 박아 두는 테스트.
 *
 * 이 순서가 뒤집히면(좌석을 잡는 시점에 발행하면) 확정되지 않고 만료될 홀드까지 예약으로
 * 전파되어, 예약 테이블에는 남았는데 좌석은 비어 있는 상태가 만들어진다.
 */
@ExtendWith(MockKExtension::class)
class ConfirmTimeTableOccupancyServiceTest {
    @MockK
    private lateinit var confirmTimeTableOccupancy: ConfirmTimeTableOccupancy

    @MockK
    private lateinit var createTimeTableOccupiedDomainEventService:
        CreateTimeTableOccupiedDomainEventService

    @MockK
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    @SpyK
    @InjectMockKs
    private lateinit var confirmTimeTableOccupancyService: ConfirmTimeTableOccupancyService

    private lateinit var pureMonkey: FixtureMonkey

    @BeforeEach
    fun init() {
        pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
        clearMocks(
            confirmTimeTableOccupancy,
            createTimeTableOccupiedDomainEventService,
            applicationEventPublisher,
        )
    }

    @DisplayName("확정할 홀드가 없을 때")
    @Nested
    inner class `Confirm but there is no hold` {
        @DisplayName("확정을 요청하면")
        @Nested
        inner class `When confirm` {
            @DisplayName("NoHoldToConfirmException이 발생하고 하류로 아무것도 나가지 않는다")
            @Test
            fun `throw and publish nothing`() {
                val command = pureMonkey.giveMeOne<ConfirmTimeTableOccupancyCommand>()

                every { confirmTimeTableOccupancy.confirm(any()) } returns null

                shouldThrow<NoHoldToConfirmException> {
                    confirmTimeTableOccupancyService.execute(command)
                }

                // 만료되었거나 이미 확정된 홀드로 하류 예약이 만들어지면 안 된다.
                verify(exactly = 0) {
                    applicationEventPublisher.publishEvent(any<TimeTableOccupiedDomainEvent>())
                }
            }
        }
    }

    @DisplayName("확정할 홀드가 있을 때")
    @Nested
    inner class `Confirm a pending hold` {
        @DisplayName("확정을 요청하면")
        @Nested
        inner class `When confirm` {
            @DisplayName("확정되고 그때서야 도메인 이벤트가 발행된다")
            @Test
            fun `confirmed and the domain event is published`() {
                val command = pureMonkey.giveMeOne<ConfirmTimeTableOccupancyCommand>()
                val confirmed =
                    ConfirmedOccupancy(
                        timeTableId = UuidGenerator.generate(),
                        timeTableOccupancyId = UuidGenerator.generate(),
                    )
                val domainEvent = pureMonkey.giveMeOne<TimeTableOccupiedDomainEvent>()

                every { confirmTimeTableOccupancy.confirm(any()) } returns confirmed
                every {
                    createTimeTableOccupiedDomainEventService.create(
                        confirmed.timeTableId,
                        confirmed.timeTableOccupancyId,
                    )
                } returns domainEvent
                every { applicationEventPublisher.publishEvent(eq(domainEvent)) } just Runs

                confirmTimeTableOccupancyService.execute(command) shouldBe true

                verify(exactly = 1) {
                    applicationEventPublisher.publishEvent(eq(domainEvent))
                }
            }

            @DisplayName("클라이언트 식별자가 아니라 인증된 userId와 슬롯으로 대상을 찾는다")
            @Test
            fun `find the hold by authenticated user and slot`() {
                val command = pureMonkey.giveMeOne<ConfirmTimeTableOccupancyCommand>()
                val inquiry = slot<ConfirmInquiry>()

                every { confirmTimeTableOccupancy.confirm(capture(inquiry)) } returns null

                shouldThrow<NoHoldToConfirmException> {
                    confirmTimeTableOccupancyService.execute(command)
                }

                inquiry.captured.userId shouldBe command.userId
                inquiry.captured.restaurantId shouldBe command.restaurantId
                inquiry.captured.date shouldBe command.date
                inquiry.captured.startTime shouldBe command.startTime
            }
        }
    }
}
