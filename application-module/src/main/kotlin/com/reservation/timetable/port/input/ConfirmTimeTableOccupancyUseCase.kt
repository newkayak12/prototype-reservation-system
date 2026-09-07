package com.reservation.timetable.port.input

import com.reservation.timetable.port.input.command.request.ConfirmTimeTableOccupancyCommand

/**
 * 임시로 잡아 둔 좌석을 사용자가 확정한다.
 *
 * 이 시스템에는 실제 결제가 없으므로 확정은 결제가 아니라 **무료 확정 액션**이다. 흐름 자체는
 * 티켓팅의 "가결제 후 N분 내 미결제 시 자동 취소"와 같다 — 좌석을 잡아만 두고 사라지는 사용자가
 * 그 자리를 영원히 묶지 않게 하는 장치다.
 */
interface ConfirmTimeTableOccupancyUseCase {
    fun execute(command: ConfirmTimeTableOccupancyCommand): Boolean
}
