package com.reservation.rest.timetable.occupancy.confirm

import com.reservation.authenticate.port.input.ExtractIdentifierFromHeaderUseCase
import com.reservation.rest.common.response.BooleanResponse
import com.reservation.rest.timetable.TimeTableOccupyUrl
import com.reservation.rest.timetable.request.ConfirmTimeTableOccupancyRequest
import com.reservation.timetable.port.input.ConfirmTimeTableOccupancyUseCase
import com.reservation.timetable.port.input.command.request.ConfirmTimeTableOccupancyCommand
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

/**
 * 잡아 둔 좌석을 확정한다.
 *
 * 예약(`POST .../booking/{restaurantId}`)은 좌석을 **임시로** 잡을 뿐이고, 정해진 시간 안에 이
 * 엔드포인트를 부르지 않으면 스케줄러가 회수한다. 실제 결제가 없는 시스템이라 확정은 결제가
 * 아니라 단순 상태 전이지만, 흐름은 티켓팅의 "가결제 → N분 내 미결제 시 자동 취소" 그대로다.
 *
 * URL에 ticketId나 점유 ID를 두지 않았다. 계획 초안은 `.../queue/{ticketId}/confirm`이었는데,
 * 서버가 인증된 `userId`와 슬롯만으로 대상을 찾을 수 있어 클라이언트가 보낸 식별자는 아무것도
 * 더해 주지 않으면서 남의 홀드를 확정하는 통로만 만든다 — 대기열 게이트에서 ticketId를 받지
 * 않기로 한 것과 같은 판단이다.
 */
@RestController
class ConfirmTimeTableOccupancyController(
    private val confirmTimeTableOccupancyUseCase: ConfirmTimeTableOccupancyUseCase,
    private val extractIdentifierFromHeaderUseCase: ExtractIdentifierFromHeaderUseCase,
) {
    @PostMapping(TimeTableOccupyUrl.BOOKING_CONFIRM)
    fun confirmTimeTableOccupancy(
        @RequestHeader header: HttpHeaders,
        @PathVariable("restaurantId") restaurantId: String,
        @RequestBody request: ConfirmTimeTableOccupancyRequest,
    ): BooleanResponse {
        val userId =
            extractIdentifierFromHeaderUseCase.execute(
                header.getFirst(HttpHeaders.AUTHORIZATION),
            )

        val command =
            ConfirmTimeTableOccupancyCommand(
                userId = userId,
                restaurantId = restaurantId,
                date = request.date,
                startTime = request.startTime,
            )

        return BooleanResponse.ok(confirmTimeTableOccupancyUseCase.execute(command))
    }
}
