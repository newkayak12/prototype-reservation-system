package com.reservation.timetable.exceptions

import com.reservation.exceptions.ClientException

/**
 * 좌석은 잡았지만 그 사실을 뒷단으로 넘기지 못했다.
 *
 * 사용자 입장에서는 "예약이 안 됐다"가 맞고 재시도하면 될 일이라 [ClientException] 계열로 둔다.
 * 이 예외가 던져지기 전에 좌석은 이미 반납된 상태여야 한다 — 안 그러면 아무도 쓰지 않는 자리가
 * TTL이 끝날 때까지 묶인다.
 */
class TimeTableOccupancyRequestNotPublishedException(
    message: String = "Failed to accept the reservation request. Please try again.",
) : ClientException(message)
