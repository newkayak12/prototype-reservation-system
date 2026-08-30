package com.reservation.queue.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.queue.port.input.AdmitSlotUseCase
import com.reservation.queue.port.input.EnterWaitingQueueUseCase
import com.reservation.queue.port.input.command.request.EnterWaitingQueueCommand
import com.reservation.queue.port.input.command.response.EnterWaitingQueueCommandResult
import com.reservation.queue.port.output.EnterWaitingQueue
import com.reservation.queue.port.output.EnterWaitingQueue.Companion.ADMITTED_POSITION
import com.reservation.queue.port.output.EnterWaitingQueue.EnterWaitingQueueInquiry
import com.reservation.queue.port.output.IsTicketAdmitted
import com.reservation.queue.port.output.IsTicketAdmitted.AdmissionInquiry
import com.reservation.queue.service.IssueWaitingTicketDomainService
import com.reservation.queue.vo.WaitingQueueSlot

/**
 * 대기열 진입.
 *
 * ## 자리가 비어 있으면 줄을 서지 않는다
 *
 * 대기열에 넣은 **직후** [AdmitSlotUseCase]로 큐를 한 번 민다. 앞에 아무도 없고 permit이
 * 남아 있으면 이번 호출에서 내가 뽑혀 나가고, 응답이 그대로 ADMITTED가 된다.
 *
 * 이게 없으면 대기열이 텅 비고 정원이 통째로 놀고 있어도 첫 사용자가 폴링 한 주기를
 * 기다린다. 붐비지 않는 시간대 — 즉 서비스의 대부분의 시간 — 에 대기열이 순수한 지연으로만
 * 작동하는 셈이라, 없느니만 못한 구간이 생긴다.
 *
 * 순서를 건너뛰는 것은 아니다. ZSET에 **먼저 넣고** 맨 앞부터 뽑기 때문에, 앞에 사람이
 * 있으면 그 사람이 뽑히고 나는 그대로 줄에 남는다.
 */
@UseCase
class EnterWaitingQueueService(
    private val issueWaitingTicketDomainService: IssueWaitingTicketDomainService,
    private val enterWaitingQueue: EnterWaitingQueue,
    private val admitSlot: AdmitSlotUseCase,
    private val isTicketAdmitted: IsTicketAdmitted,
) : EnterWaitingQueueUseCase {
    override fun execute(command: EnterWaitingQueueCommand): EnterWaitingQueueCommandResult {
        val slot =
            WaitingQueueSlot(
                restaurantId = command.restaurantId,
                date = command.date,
                startTime = command.startTime,
            )

        // 발급한 nonce는 어디까지나 후보다. 이 사용자가 이미 대기열에 있었다면 진입 시점에
        // 먼저 실린 티켓이 돌아오고, 이번 후보는 버려진다.
        val candidate = issueWaitingTicketDomainService.issue(command.userId, slot)
        val entered =
            enterWaitingQueue.enter(
                EnterWaitingQueueInquiry(
                    slot = slot,
                    userId = command.userId,
                    ticketId = candidate.ticketId,
                ),
            )

        val ticket =
            if (entered.ticketId == candidate.ticketId) {
                candidate
            } else {
                issueWaitingTicketDomainService.restore(command.userId, slot, entered.ticketId)
            }

        val position = admitNow(slot, entered.ticketId, entered.position)

        // 이미 입장이 허용된 티켓은 대기열로 되돌리지 않는다.
        if (position == ADMITTED_POSITION) {
            ticket.admitted()
        } else {
            ticket.enqueued(position)
        }

        return EnterWaitingQueueCommandResult(
            ticketId = ticket.ticketId,
            position = position,
        )
    }

    /**
     * 큐를 한 번 밀고, 그 결과 내가 뽑혔는지 확인한다.
     *
     * 이미 ADMITTED로 돌아온 티켓(재진입)은 건드리지 않는다 — 한 번 더 밀 이유도 없고,
     * 승격 여부를 다시 조회할 이유도 없다.
     */
    private fun admitNow(
        slot: WaitingQueueSlot,
        ticketId: String,
        position: Long,
    ): Long {
        if (position == ADMITTED_POSITION) return position

        admitSlot.execute(slot)

        val admitted =
            isTicketAdmitted.query(AdmissionInquiry(slot = slot, ticketId = ticketId))

        return if (admitted) ADMITTED_POSITION else position
    }
}
