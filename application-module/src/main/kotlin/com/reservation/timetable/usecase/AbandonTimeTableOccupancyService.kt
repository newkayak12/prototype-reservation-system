package com.reservation.timetable.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.timetable.port.input.AbandonTimeTableOccupancyUseCase
import com.reservation.timetable.port.input.command.request.OccupyTimeTableCommand
import com.reservation.timetable.port.output.ReleaseTimeTableSeat
import com.reservation.timetable.port.output.ReleaseTimeTableSeat.SeatReleaseInquiry

/**
 * 저장에 최종 실패한 요청의 좌석을 회수한다.
 *
 * 되돌리지 않으면 그 한 자리는 아무도 쓰지 못한 채 카운터 TTL이 끝날 때까지 묶인다 —
 * 오버부킹의 반대인 언더부킹이고, 좌석이 30개뿐인 슬롯에서는 이쪽도 충분히 아프다.
 */
@UseCase
class AbandonTimeTableOccupancyService(
    private val releaseTimeTableSeat: ReleaseTimeTableSeat,
) : AbandonTimeTableOccupancyUseCase {
    override fun execute(command: OccupyTimeTableCommand) {
        releaseTimeTableSeat.release(
            SeatReleaseInquiry(
                restaurantId = command.restaurantId,
                date = command.date,
                startTime = command.startTime,
                userId = command.userId,
            ),
        )
    }
}
