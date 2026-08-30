package com.reservation.timetable.port.output

import java.time.LocalDate
import java.time.LocalTime

/**
 * 임시 홀드를 확정으로 전환한다.
 *
 * 확정 대상은 **인증에서 얻은 `userId`와 슬롯으로 서버가 직접 찾는다.** 클라이언트가 보낸 점유
 * ID를 받으면 남의 홀드 ID를 넣어 확정해 버리는 경로가 생긴다 — 대기열 게이트에서 ticketId를
 * 받지 않기로 한 것과 같은 이유다.
 */
interface ConfirmTimeTableOccupancy {
    /** @return 확정된 점유. 확정할 홀드가 없거나 이미 확정/만료됐으면 null. */
    fun confirm(inquiry: ConfirmInquiry): ConfirmedOccupancy?

    data class ConfirmInquiry(
        val userId: String,
        val restaurantId: String,
        val date: LocalDate,
        val startTime: LocalTime,
    )

    data class ConfirmedOccupancy(
        val timeTableId: String,
        val timeTableOccupancyId: String,
    )
}
