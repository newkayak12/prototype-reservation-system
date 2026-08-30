package com.reservation.timetable.port.output

import java.time.LocalDate
import java.time.LocalTime

/**
 * "이 슬롯에서 이 사용자 몫으로 좌석 한 자리를 확보한다"를 **한 번의 원자 연산**으로 처리한다.
 *
 * 중복 예약 차단과 좌석 차감을 굳이 한 덩어리로 묶은 이유가 있다. 둘을 나눠 부르면
 * "중복이 아님을 확인했다"와 "자리를 잡았다" 사이에 창이 생기고, 그 창으로 같은 사용자의
 * 두 번째 요청이 들어오면 한 사람이 두 자리를 먹는다. 두 키를 같은 Lua 스크립트 안에서
 * 건드리면 그 창 자체가 존재하지 않는다.
 *
 * ## 이것이 분산락을 대체한다
 *
 * 이전에는 `@DistributedLock(FAIR_LOCK, waitTime = 2분)`이 슬롯 단위로 요청을 **직렬화**해서
 * 정합성을 지켰다. 정확하긴 하지만 대기가 곧 응답시간이라 부하가 오를수록 선형으로 느려지고,
 * 2분 안에 차례가 오지 않으면 그냥 실패한다. 여기서는 직렬화하는 대신 Redis 단일 스레드가
 * 스크립트를 원자적으로 실행한다는 성질에 기댄다 — 경합해도 아무도 기다리지 않고, 진 쪽은
 * 즉시 [SeatAcquisition.SOLD_OUT]을 받는다.
 */
interface AcquireTimeTableSeat {
    fun acquire(inquiry: SeatInquiry): SeatAcquisition

    data class SeatInquiry(
        val restaurantId: String,
        val date: LocalDate,
        val startTime: LocalTime,
        val userId: String,
        /**
         * 좌석 카운터가 아직 없을 때 심을 초기값(= 지금 예약 가능한 timetable 개수).
         * 키가 이미 있으면 무시된다 — 먼저 심은 값이 언제나 이긴다.
         */
        val availableSeats: Int,
    )

    enum class SeatAcquisition {
        ACQUIRED,

        /** 이 사용자가 이 슬롯을 이미 잡았거나 처리 중이다. */
        DUPLICATED,

        /** 남은 좌석이 없다. */
        SOLD_OUT,
    }
}
