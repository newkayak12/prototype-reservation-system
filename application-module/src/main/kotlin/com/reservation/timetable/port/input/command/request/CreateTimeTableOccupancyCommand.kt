package com.reservation.timetable.port.input.command.request

import java.time.LocalDate
import java.time.LocalTime

/**
 * 대기열 티켓 ID는 담지 않는다.
 * 게이트가 `userId` + 슬롯으로부터 서버에서 직접 파생하므로 클라이언트가 보낸 값을 받을
 * 이유가 없고, 받으면 그 자체로 게이트 우회 통로가 된다.
 */
data class CreateTimeTableOccupancyCommand(
    val userId: String,
    val restaurantId: String,
    val date: LocalDate,
    val startTime: LocalTime,
)
