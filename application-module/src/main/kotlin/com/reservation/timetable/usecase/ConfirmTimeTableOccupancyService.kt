package com.reservation.timetable.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.timetable.exceptions.NoHoldToConfirmException
import com.reservation.timetable.port.input.ConfirmTimeTableOccupancyUseCase
import com.reservation.timetable.port.input.command.request.ConfirmTimeTableOccupancyCommand
import com.reservation.timetable.port.output.ConfirmTimeTableOccupancy
import com.reservation.timetable.port.output.ConfirmTimeTableOccupancy.ConfirmInquiry
import com.reservation.timetable.service.CreateTimeTableOccupiedDomainEventService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Transactional

/**
 * 임시 홀드를 확정으로 전환하고, 그때서야 하류로 도메인 이벤트를 발행한다.
 *
 * ## 왜 이벤트가 여기로 옮겨왔나
 *
 * 이전에는 좌석을 저장하는 시점([OccupyTimeTableService])에 이벤트를 발행했다. 홀드 흐름이
 * 들어오면서 그 시점의 점유는 아직 PENDING이고, 사용자가 확정하지 않으면 만료되어 사라진다.
 * 거기서 이벤트를 보내면 **결국 취소될 홀드까지 하류 예약으로 전파된다** — 예약 테이블에는
 * 남았는데 좌석은 비어 있는 상태가 만들어진다.
 *
 * 그래서 발행 시점을 "좌석을 잡았을 때"가 아니라 "사용자가 쓰겠다고 확정했을 때"로 옮겼다.
 */
@UseCase
class ConfirmTimeTableOccupancyService(
    private val confirmTimeTableOccupancy: ConfirmTimeTableOccupancy,
    private val createTimeTableOccupiedDomainEventService:
        CreateTimeTableOccupiedDomainEventService,
    private val applicationEventPublisher: ApplicationEventPublisher,
) : ConfirmTimeTableOccupancyUseCase {
    @Transactional
    override fun execute(command: ConfirmTimeTableOccupancyCommand): Boolean {
        val confirmed =
            confirmTimeTableOccupancy.confirm(
                ConfirmInquiry(
                    userId = command.userId,
                    restaurantId = command.restaurantId,
                    date = command.date,
                    startTime = command.startTime,
                ),
            ) ?: throw NoHoldToConfirmException()

        val domainEvent =
            createTimeTableOccupiedDomainEventService.create(
                confirmed.timeTableId,
                confirmed.timeTableOccupancyId,
            )
        applicationEventPublisher.publishEvent(domainEvent)

        return true
    }
}
