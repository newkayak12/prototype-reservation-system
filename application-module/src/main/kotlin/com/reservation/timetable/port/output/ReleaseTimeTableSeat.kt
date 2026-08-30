package com.reservation.timetable.port.output

import java.time.LocalDate
import java.time.LocalTime

/**
 * [AcquireTimeTableSeat]로 잡아 둔 자리를 되돌린다 — 좌석을 한 자리 돌려주고 중복 마커를 지운다.
 *
 * 좌석을 확보한 뒤 DB 저장이 실패하면 Redis에는 "팔린 자리"가, DB에는 아무것도 없는 상태가
 * 남는다. 그대로 두면 그 자리는 카운터 TTL이 끝날 때까지 아무도 살 수 없는 유령 좌석이 되고,
 * 중복 마커까지 남아 그 사용자는 재시도조차 못 한다. 그래서 되돌림은 선택이 아니라 필수다.
 */
interface ReleaseTimeTableSeat {
    fun release(inquiry: SeatReleaseInquiry)

    data class SeatReleaseInquiry(
        val restaurantId: String,
        val date: LocalDate,
        val startTime: LocalTime,
        val userId: String,
    )
}
