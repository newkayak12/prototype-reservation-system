package com.reservation.timetable.usecase

import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.timetable.TimeTable
import com.reservation.timetable.exceptions.AllTheSeatsAreAlreadyOccupiedException
import com.reservation.timetable.exceptions.AllTheThingsAreAlreadyOccupiedException
import com.reservation.timetable.policy.exceptions.InvalidTimeTableIdException
import com.reservation.timetable.policy.exceptions.InvalidTimeTableStatusException
import com.reservation.timetable.policy.exceptions.InvalidTimeTableUserIdException
import com.reservation.timetable.port.input.command.request.OccupyTimeTableCommand
import com.reservation.timetable.port.output.ClaimBookableTimeTable
import com.reservation.timetable.port.output.ClaimBookableTimeTable.ClaimBookableTimeTableInquiry
import com.reservation.timetable.port.output.CreateTimeTableOccupancy
import com.reservation.timetable.service.CreateTimeTableOccupancyDomainService
import com.reservation.timetable.snapshot.TimeTableSnapshot
import com.reservation.timetable.snapshot.TimetableOccupancySnapShot
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import net.jqwik.api.Arbitraries
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.dao.DataIntegrityViolationException

/**
 * 컨슈머가 호출하는 저장 경로.
 *
 * Phase 4에서 두 가지가 달라졌다.
 * 1. 좌석 행을 **조건부 갱신으로 가져가면서** 고른다 ([ClaimBookableTimeTable]). 목록을 읽어
 *    첫 행을 집던 방식은 읽는 순간과 저장하는 순간 사이에 창이 있었다.
 * 2. 도메인 이벤트를 여기서 발행하지 않는다. 이 시점의 점유는 아직 PENDING이라,
 *    확정되지 않고 만료될 홀드까지 하류로 전파되면 안 된다.
 */
@ExtendWith(MockKExtension::class)
class OccupyTimeTableServiceTest {
    @MockK
    private lateinit var claimBookableTimeTable: ClaimBookableTimeTable

    @MockK
    private lateinit var createTimeTableOccupancy: CreateTimeTableOccupancy

    @MockK
    private lateinit var createTimeTableOccupancyDomainService:
        CreateTimeTableOccupancyDomainService

    @SpyK
    @InjectMockKs
    private lateinit var occupyTimeTableService: OccupyTimeTableService

    private lateinit var pureMonkey: FixtureMonkey

    @BeforeEach
    fun init() {
        pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
        clearMocks(
            claimBookableTimeTable,
            createTimeTableOccupancy,
            createTimeTableOccupancyDomainService,
        )
    }

    private fun snapshot(): TimeTableSnapshot =
        pureMonkey.giveMeBuilder<TimeTableSnapshot>()
            .set("id", UuidGenerator.generate())
            .set("timetableOccupancy", pureMonkey.giveMeOne<TimetableOccupancySnapShot>())
            .sample()

    @DisplayName("소비 시점에 남은 좌석이 하나도 없을 때")
    @Nested
    inner class `Consume but there is no bookable seat` {
        @DisplayName("점유를 저장하려 하면")
        @Nested
        inner class `When occupy` {
            @DisplayName("AllTheSeatsAreAlreadyOccupiedException이 발생한다")
            @Test
            fun `throw AllTheSeatsAreAlreadyOccupiedException`() {
                val command = pureMonkey.giveMeOne<OccupyTimeTableCommand>()

                every { claimBookableTimeTable.claim(any()) } returns null

                shouldThrow<AllTheSeatsAreAlreadyOccupiedException> {
                    occupyTimeTableService.execute(command)
                }

                verify(exactly = 0) {
                    createTimeTableOccupancy.createTimeTableOccupancy(any())
                }
            }
        }
    }

    @DisplayName("도메인 검증에 실패하는 요청을 소비했을 때")
    @Nested
    inner class `Consume but domain exceptions throw` {
        @DisplayName("점유를 저장하려 하면")
        @Nested
        inner class `When occupy` {
            @DisplayName("해당하는 도메인 예외가 그대로 올라온다")
            @Test
            fun `throw DomainException`() {
                val command = pureMonkey.giveMeOne<OccupyTimeTableCommand>()

                every {
                    claimBookableTimeTable.claim(any())
                } returns pureMonkey.giveMeOne<TimeTable>()
                every { createTimeTableOccupancyDomainService.create(any(), any()) } throws
                    Arbitraries.of(
                        InvalidTimeTableIdException(),
                        InvalidTimeTableUserIdException(),
                        InvalidTimeTableStatusException(),
                    ).sample()

                val exception = shouldThrowAny { occupyTimeTableService.execute(command) }

                exception.message shouldContain "Invalid"

                verify(exactly = 0) {
                    createTimeTableOccupancy.createTimeTableOccupancy(any())
                }
            }
        }
    }

    @DisplayName("DB 저장에 실패했을 때")
    @Nested
    inner class `Consume but save database is failed` {
        @DisplayName("점유를 저장하려 하면")
        @Nested
        inner class `When occupy` {
            // 여기서 좌석을 되돌리지 않는 것이 핵심이다. 재시도가 남아 있는데 반납하면
            // 그 자리를 다른 사용자가 가져가고, 뒤이은 재시도까지 성공하면서 오버부킹이 된다.
            @DisplayName("예외를 그대로 던져 재시도에 맡긴다")
            @Test
            fun `insert into database is failed`() {
                val command = pureMonkey.giveMeOne<OccupyTimeTableCommand>()

                every {
                    claimBookableTimeTable.claim(any())
                } returns pureMonkey.giveMeOne<TimeTable>()
                every {
                    createTimeTableOccupancyDomainService.create(any(), any())
                } returns snapshot()
                every {
                    createTimeTableOccupancy.createTimeTableOccupancy(any())
                } throws DataIntegrityViolationException(Arbitraries.strings().sample())

                shouldThrow<DataIntegrityViolationException> {
                    occupyTimeTableService.execute(command)
                }
            }

            @DisplayName("저장이 아무것도 돌려주지 않으면 예외가 발생한다")
            @Test
            fun `throw when nothing is persisted`() {
                val command = pureMonkey.giveMeOne<OccupyTimeTableCommand>()

                every {
                    claimBookableTimeTable.claim(any())
                } returns pureMonkey.giveMeOne<TimeTable>()
                every {
                    createTimeTableOccupancyDomainService.create(any(), any())
                } returns snapshot()
                every { createTimeTableOccupancy.createTimeTableOccupancy(any()) } returns null

                shouldThrow<AllTheThingsAreAlreadyOccupiedException> {
                    occupyTimeTableService.execute(command)
                }
            }
        }
    }

    @DisplayName("정상적인 요청을 소비했을 때")
    @Nested
    inner class `Consume a valid request` {
        @DisplayName("점유를 저장하려 하면")
        @Nested
        inner class `When occupy` {
            @DisplayName("점유가 저장된다")
            @Test
            fun `occupancy is saved`() {
                val command = pureMonkey.giveMeOne<OccupyTimeTableCommand>()

                every {
                    claimBookableTimeTable.claim(any())
                } returns pureMonkey.giveMeOne<TimeTable>()
                every {
                    createTimeTableOccupancyDomainService.create(any(), any())
                } returns snapshot()
                every {
                    createTimeTableOccupancy.createTimeTableOccupancy(any())
                } returns UuidGenerator.generate()

                occupyTimeTableService.execute(command) shouldBe true

                verify(exactly = 1) {
                    createTimeTableOccupancy.createTimeTableOccupancy(any())
                }
            }

            // 발행 시점에 특정 행을 찍어 두지 않은 이유가 이것이다 — 어느 행을 줄지는
            // 소비 시점에 가져가면서 정한다.
            @DisplayName("명령의 슬롯으로 좌석 행을 가져간다")
            @Test
            fun `claim the timetable row at consumption time`() {
                val command = pureMonkey.giveMeOne<OccupyTimeTableCommand>()
                val claimed = pureMonkey.giveMeOne<TimeTable>()
                val inquiry = slot<ClaimBookableTimeTableInquiry>()
                val chosen = slot<TimeTable>()

                every { claimBookableTimeTable.claim(capture(inquiry)) } returns claimed
                every {
                    createTimeTableOccupancyDomainService.create(any(), capture(chosen))
                } returns snapshot()
                every {
                    createTimeTableOccupancy.createTimeTableOccupancy(any())
                } returns UuidGenerator.generate()

                occupyTimeTableService.execute(command)

                inquiry.captured.restaurantId shouldBe command.restaurantId
                inquiry.captured.date shouldBe command.date
                inquiry.captured.startTime shouldBe command.startTime
                // 가져간 바로 그 행에 점유를 붙여야 한다.
                chosen.captured shouldBe claimed
            }
        }
    }
}
