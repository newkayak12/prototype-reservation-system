package com.reservation.kafka.event

import java.time.LocalDate
import java.time.LocalTime

/**
 * "이 사용자 몫으로 이 슬롯에 한 자리를 잡았다"는 사실만 담는다.
 *
 * `timetableId`가 없다는 점이 이 페이로드의 핵심이다. 어느 행을 줄지는 컨슈머가 소비 시점에
 * 새로 고른다 — 발행 시점에 골라 두면 그 행이 소비될 때까지 비어 있으리라는 보장이 없다.
 *
 * 대기열 `ticketId`도 담지 않는다. 여기까지 온 메시지는 이미 게이트를 통과했고, 다시 검사할
 * 것도 아닌 값을 실어 나를 이유가 없다.
 */
data class TimeTableOccupancyRequestedEvent(
    val restaurantId: String,
    val date: LocalDate,
    val startTime: LocalTime,
    val userId: String,
)
