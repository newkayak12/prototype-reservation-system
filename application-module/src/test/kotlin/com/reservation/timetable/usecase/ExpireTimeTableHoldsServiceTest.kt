package com.reservation.timetable.usecase

import com.reservation.timetable.port.output.ExpireTimeTableHolds
import com.reservation.timetable.port.output.ExpireTimeTableHolds.ExpireInquiry
import com.reservation.timetable.port.output.ExpireTimeTableHolds.ExpiredHold
import com.reservation.timetable.port.output.ReleaseTimeTableSeat
import com.reservation.timetable.port.output.ReleaseTimeTableSeat.SeatReleaseInquiry
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 만료 회수가 **DB와 Redis 양쪽**을 되돌리는지 확인한다.
 *
 * 한쪽만 되돌리면 조용히 망가진다. DB에서 점유만 풀고 좌석 카운터를 두면 그 자리는 DB에서는
 * 비어 있는데 카운터상으로는 팔린 상태로 남아 아무도 살 수 없고, 중복 마커를 안 지우면 홀드가
 * 만료된 사용자가 다시 예약할 때 "이미 예약했다"로 거절당한다.
 */
class ExpireTimeTableHoldsServiceTest : FunSpec(
    {
        val expireTimeTableHolds = mockk<ExpireTimeTableHolds>()
        val releaseTimeTableSeat = mockk<ReleaseTimeTableSeat>()
        val holdTimeToLiveSeconds = 300L
        val batchSize = 200

        val service =
            ExpireTimeTableHoldsService(
                expireTimeTableHolds,
                releaseTimeTableSeat,
                holdTimeToLiveSeconds,
                batchSize,
            )

        fun hold(userId: String) =
            ExpiredHold(
                restaurantId = UuidGenerator.generate(),
                date = LocalDate.of(2026, 8, 28),
                startTime = LocalTime.of(18, 0),
                userId = userId,
            )

        beforeTest { clearAllMocks() }

        test("회수한 홀드마다 Redis 좌석을 되돌린다.") {
            val holds = listOf(hold("a"), hold("b"), hold("c"))
            val released = mutableListOf<SeatReleaseInquiry>()

            every { expireTimeTableHolds.expire(any()) } returns holds
            every { releaseTimeTableSeat.release(capture(released)) } just Runs

            service.execute() shouldBe holds.size

            released.map { it.userId } shouldBe listOf("a", "b", "c")
            released.first().restaurantId shouldBe holds.first().restaurantId
        }

        test("회수할 홀드가 없으면 Redis를 건드리지 않는다.") {
            every { expireTimeTableHolds.expire(any()) } returns emptyList()

            service.execute() shouldBe 0

            // 되돌릴 것이 없는데 release를 부르면 남의 좌석 카운터를 올려 준다.
            verify(exactly = 0) { releaseTimeTableSeat.release(any()) }
        }

        test("설정된 유효시간보다 먼저 잡힌 홀드만 회수 대상으로 넘긴다.") {
            val inquiry = slot<ExpireInquiry>()
            val before = LocalDateTime.now().minusSeconds(holdTimeToLiveSeconds)

            every { expireTimeTableHolds.expire(capture(inquiry)) } returns emptyList()

            service.execute()

            inquiry.captured.limit shouldBe batchSize
            // 경계가 미래로 새면 아직 유효한 홀드까지 취소된다.
            (inquiry.captured.heldBefore >= before.minusSeconds(5)) shouldBe true
            (inquiry.captured.heldBefore <= before.plusSeconds(5)) shouldBe true
        }
    },
)
