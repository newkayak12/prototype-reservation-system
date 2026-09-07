package com.reservation.rest.timetable.request

import java.time.LocalDate
import java.time.LocalTime

/**
 * 확정 대상은 슬롯으로만 지정한다. 사용자는 인증에서 얻고, 점유 ID는 서버가 직접 찾는다.
 */
data class ConfirmTimeTableOccupancyRequest(
    val date: LocalDate,
    val startTime: LocalTime,
)
