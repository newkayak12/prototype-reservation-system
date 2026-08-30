package com.reservation.timetable.usecase

import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.timetable.port.input.command.request.OccupyTimeTableCommand
import com.reservation.timetable.port.output.ReleaseTimeTableSeat
import com.reservation.timetable.port.output.ReleaseTimeTableSeat.SeatReleaseInquiry
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * 되돌림이 DLT 직전 좌석 정리를 정확히 위임하는지 못 박아 두는 테스트.
 *
 * [SeatReleaseInquiry]의 restaurantId/userId는 둘 다 String이라 뒤바뀌어도 컴파일이
 * 통과한다 — 잘못된 사용자에게서 좌석을 빼앗거나 엉뚱한 매장의 카운터를 건드리는 사고가
 * 런타임에만 드러난다. 그래서 필드 단위로 개별 단언해 매핑을 고정한다.
 */
@ExtendWith(MockKExtension::class)
class AbandonTimeTableOccupancyServiceTest {
    @MockK
    private lateinit var releaseTimeTableSeat: ReleaseTimeTableSeat

    @InjectMockKs
    private lateinit var abandonTimeTableOccupancyService: AbandonTimeTableOccupancyService

    private lateinit var pureMonkey: FixtureMonkey

    @BeforeEach
    fun init() {
        pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
        clearMocks(releaseTimeTableSeat)
    }

    @DisplayName("되돌릴 좌석이 있을 때")
    @Nested
    inner class `Abandon a seat` {
        @DisplayName("회수를 요청하면")
        @Nested
        inner class `When abandon` {
            @DisplayName("좌석 반환이 정확히 한 번 위임된다")
            @Test
            fun `delegate release exactly once`() {
                val command = pureMonkey.giveMeOne<OccupyTimeTableCommand>()

                every { releaseTimeTableSeat.release(any()) } just Runs

                abandonTimeTableOccupancyService.execute(command)

                // 재시도가 모두 소진된 뒤의 유일한 정리 지점이다 — 두 번 불리면 이중 반환,
                // 안 불리면 유령 좌석이 카운터 TTL이 끝날 때까지 묶인다.
                verify(exactly = 1) { releaseTimeTableSeat.release(any()) }
            }

            @DisplayName("restaurantId와 userId를 뒤바꾸지 않고 각 필드를 그대로 전달한다")
            @Test
            fun `pass through each field without swapping restaurantId and userId`() {
                val command = pureMonkey.giveMeOne<OccupyTimeTableCommand>()
                val inquiry = slot<SeatReleaseInquiry>()

                every { releaseTimeTableSeat.release(capture(inquiry)) } just Runs

                abandonTimeTableOccupancyService.execute(command)

                inquiry.captured.restaurantId shouldBe command.restaurantId
                inquiry.captured.userId shouldBe command.userId
                inquiry.captured.date shouldBe command.date
                inquiry.captured.startTime shouldBe command.startTime
                inquiry.captured shouldBe
                    SeatReleaseInquiry(
                        restaurantId = command.restaurantId,
                        date = command.date,
                        startTime = command.startTime,
                        userId = command.userId,
                    )
            }
        }
    }

    @DisplayName("포트가 반환에 실패할 때")
    @Nested
    inner class `Release fails` {
        @DisplayName("회수를 요청하면")
        @Nested
        inner class `When abandon` {
            @DisplayName("예외를 삼키지 않고 그대로 전파한다")
            @Test
            fun `propagate the exception instead of swallowing it`() {
                val command = pureMonkey.giveMeOne<OccupyTimeTableCommand>()

                every {
                    releaseTimeTableSeat.release(
                        any(),
                    )
                } throws IllegalStateException("release failed")

                shouldThrow<IllegalStateException> {
                    abandonTimeTableOccupancyService.execute(command)
                }
            }
        }
    }
}
