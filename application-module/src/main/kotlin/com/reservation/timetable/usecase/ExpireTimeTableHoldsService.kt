package com.reservation.timetable.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.timetable.port.input.ExpireTimeTableHoldsUseCase
import com.reservation.timetable.port.output.ExpireTimeTableHolds
import com.reservation.timetable.port.output.ExpireTimeTableHolds.ExpireInquiry
import com.reservation.timetable.port.output.ExpireTimeTableHolds.ExpiredHold
import com.reservation.timetable.port.output.ReleaseTimeTableSeat
import com.reservation.timetable.port.output.ReleaseTimeTableSeat.SeatReleaseInquiry
import org.springframework.beans.factory.annotation.Value
import java.time.LocalDateTime

/**
 * 만료된 홀드를 DB와 Redis 양쪽에서 회수한다.
 *
 * ## 두 곳을 모두 되돌려야 하는 이유
 *
 * DB에서 점유만 풀고 Redis 좌석 카운터를 그대로 두면, 그 자리는 **DB에서는 비어 있는데
 * 카운터상으로는 팔린 상태**로 남는다. 예약 요청은 카운터에서 먼저 걸러지므로 아무도 그 좌석에
 * 도달하지 못하고, 결과적으로 영원히 안 팔리는 좌석이 하나 생긴다.
 *
 * 중복 마커(`DEDUP`)도 같이 지워야 한다. 안 지우면 홀드가 만료된 사용자가 다시 예약하려 할 때
 * "이미 예약했다"로 거절당한다 — 정작 그 사람의 예약은 방금 취소됐는데도.
 *
 * [ReleaseTimeTableSeat]이 그 둘을 한 번에 처리하므로 새 포트를 만들지 않고 재사용한다.
 *
 * ## 순서
 *
 * DB를 먼저 풀고 Redis를 나중에 되돌린다. 반대로 하면 Redis에서 자리가 열린 직후 새 요청이
 * 들어오는데 DB에는 아직 이전 홀드가 남아 있어, 그 요청이 행 잠금 뒤에서 헛돌다 실패한다.
 * 이 순서라면 최악의 경우가 "잠깐 카운터가 실제보다 보수적인" 것뿐이고, 그건 안전한 방향이다.
 */
@UseCase
class ExpireTimeTableHoldsService(
    private val expireTimeTableHolds: ExpireTimeTableHolds,
    private val releaseTimeTableSeat: ReleaseTimeTableSeat,
    @Value("\${reservation.timetable.hold-time-to-live-seconds:300}")
    private val holdTimeToLiveSeconds: Long,
    @Value("\${reservation.timetable.hold-expiry-batch-size:200}")
    private val expiryBatchSize: Int,
) : ExpireTimeTableHoldsUseCase {
    override fun execute(): Int {
        val expired =
            expireTimeTableHolds.expire(
                ExpireInquiry(
                    heldBefore = LocalDateTime.now().minusSeconds(holdTimeToLiveSeconds),
                    limit = expiryBatchSize,
                ),
            )

        expired.forEach { restoreSeat(it) }

        return expired.size
    }

    private fun restoreSeat(hold: ExpiredHold) {
        releaseTimeTableSeat.release(
            SeatReleaseInquiry(
                restaurantId = hold.restaurantId,
                date = hold.date,
                startTime = hold.startTime,
                userId = hold.userId,
            ),
        )
    }
}
