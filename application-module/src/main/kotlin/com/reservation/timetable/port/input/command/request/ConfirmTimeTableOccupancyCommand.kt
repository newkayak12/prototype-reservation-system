package com.reservation.timetable.port.input.command.request

import java.time.LocalDate
import java.time.LocalTime

/**
 * 점유 ID를 받지 않는다.
 *
 * 클라이언트가 보낸 점유 ID를 믿으면 남의 홀드 ID를 넣어 확정해 버리는 경로가 생긴다.
 * 인증에서 얻은 `userId`와 슬롯만으로 서버가 대상을 찾는다 — 대기열 게이트에서 ticketId를
 * 받지 않기로 한 것과 같은 이유다.
 */
data class ConfirmTimeTableOccupancyCommand(
    val userId: String,
    val restaurantId: String,
    val date: LocalDate,
    val startTime: LocalTime,
)
