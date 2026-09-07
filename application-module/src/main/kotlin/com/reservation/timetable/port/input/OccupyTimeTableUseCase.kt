package com.reservation.timetable.port.input

import com.reservation.timetable.port.input.command.request.OccupyTimeTableCommand

/**
 * 좌석 확보가 끝난 요청을 실제로 저장한다. 메시지 컨슈머가 호출하는 진입점이다.
 *
 * 사용자 요청 경로([CreateTimeTableOccupancyUseCase])에서 떼어낸 뒷부분이다. 앞쪽은 "이 요청이
 * 자리를 차지할 자격이 있는가"만 판단하고, 여기서는 그 자격을 전제로 저장만 한다.
 */
interface OccupyTimeTableUseCase {
    fun execute(command: OccupyTimeTableCommand): Boolean
}
