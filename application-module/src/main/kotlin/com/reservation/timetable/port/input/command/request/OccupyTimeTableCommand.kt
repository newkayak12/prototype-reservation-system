package com.reservation.timetable.port.input.command.request

import java.time.LocalDate
import java.time.LocalTime

/**
 * 좌석 확보가 이미 끝난 요청을 실제로 저장하라는 명령.
 *
 * [CreateTimeTableOccupancyCommand]와 필드가 같지만 의미가 다르다. 저쪽은 "예약을 시도한다"이고
 * 이쪽은 "Redis에서 이미 한 자리를 확보했으니 저장만 하면 된다"이다. 그래서 이 명령을 처리하는
 * 쪽은 중복 검사도, 좌석 차감도 다시 하지 않는다.
 */
data class OccupyTimeTableCommand(
    val userId: String,
    val restaurantId: String,
    val date: LocalDate,
    val startTime: LocalTime,
)
