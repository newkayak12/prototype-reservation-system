package com.reservation.timetable.port.output

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 확정되지 않은 채 시간이 지난 홀드를 회수한다.
 *
 * 이 장치가 없으면 "좌석은 잡았는데 확정하지 않고 사라진" 사용자가 그 자리를 영원히 묶는다.
 * 좌석이 30개뿐인 슬롯에서는 몇 명만 그래도 매장이 통째로 팔 수 없는 상태가 된다.
 *
 * 회수된 슬롯 정보를 돌려주는 이유는 **Redis 쪽도 같이 되돌려야** 하기 때문이다. DB에서만
 * 풀고 좌석 카운터를 그대로 두면, 그 자리는 DB에서는 비어 있는데 카운터상으로는 팔린 상태로
 * 남아 아무도 살 수 없다.
 */
interface ExpireTimeTableHolds {
    fun expire(inquiry: ExpireInquiry): List<ExpiredHold>

    data class ExpireInquiry(
        /** 이 시각보다 먼저 잡힌 홀드가 회수 대상이다. */
        val heldBefore: LocalDateTime,
        /** 한 번에 처리할 최대 건수. 한 주기가 지나치게 길어지지 않게 끊는다. */
        val limit: Int,
    )

    data class ExpiredHold(
        val restaurantId: String,
        val date: LocalDate,
        val startTime: LocalTime,
        val userId: String,
    )
}
