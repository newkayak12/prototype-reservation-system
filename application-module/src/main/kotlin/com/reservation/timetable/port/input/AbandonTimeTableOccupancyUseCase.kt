package com.reservation.timetable.port.input

import com.reservation.timetable.port.input.command.request.OccupyTimeTableCommand

/**
 * 끝내 저장하지 못한 요청의 좌석을 되돌린다.
 *
 * 재시도를 모두 소진해 DLT로 보내는 시점에만 호출한다. **중간 실패에서 부르면 안 된다** —
 * 재시도가 남아 있는데 좌석을 반납하면 그 자리를 다른 사용자가 가져가고, 이후 재시도가
 * 성공하면서 좌석 수보다 많은 예약이 저장된다. 즉 되돌리기가 오버부킹을 만든다.
 */
interface AbandonTimeTableOccupancyUseCase {
    fun execute(command: OccupyTimeTableCommand)
}
