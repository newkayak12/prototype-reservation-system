package com.reservation.queue.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.enumeration.QueueStatus
import com.reservation.enumeration.QueueStatus.ADMITTED
import com.reservation.enumeration.QueueStatus.EXPIRED
import com.reservation.enumeration.QueueStatus.WAITING
import com.reservation.queue.port.input.AdmitSlotUseCase
import com.reservation.queue.port.input.InquiryWaitingTicketUseCase
import com.reservation.queue.port.input.query.request.InquiryWaitingTicketQuery
import com.reservation.queue.port.input.query.response.InquiryWaitingTicketQueryResult
import com.reservation.queue.port.output.FindTicketResult
import com.reservation.queue.port.output.FindWaitingTicketPosition
import com.reservation.queue.port.output.FindWaitingTicketPosition.WaitingTicketInquiry
import com.reservation.queue.port.output.IsTicketAdmitted
import com.reservation.queue.port.output.IsTicketAdmitted.AdmissionInquiry
import com.reservation.queue.vo.WaitingQueueSlot

/**
 * 대기열 순번/상태 폴링. **그리고 이 요청이 대기열을 민다.**
 *
 * 판정 순서는 "최종 결과 → 입장 허용 → 대기 중 → 만료"이다.
 * `RESULT:{ticketId}`(PENDING/CONFIRMED/CANCELLED)는 Phase 4가 채우므로, 이번 Phase에서는
 * 값이 있으면 그대로 돌려주고 없으면 ADMITTED/WAITING 계산으로 폴백한다.
 *
 * ## 조회인데 왜 상태를 바꾸는가
 *
 * 승격을 타이머에 맡겼더니 주기가 곧 처리량 상한이 됐다(자세한 경위는
 * [AdmitWaitingQueueService]). 그런데 **기다리는 사람은 반드시 폴링 중이다.** 그러니 큐를
 * 밀 계기를 따로 만들 필요 없이 폴링 자체를 계기로 쓰면 된다. 아무도 폴링하지 않는다면
 * 그건 기다리는 사람이 없다는 뜻이고, 그때는 큐를 밀 이유도 없다.
 *
 * 조회 유스케이스가 쓰기를 하는 것은 분명한 냄새다. 그래도 이쪽을 택한 이유는, 대안이
 * "아무 일도 일어나지 않는데 5,000명의 슬롯을 500ms마다 훑는 워커"이기 때문이다. 부하가
 * 요청에 비례해 발생하는 편이 시간에 비례해 발생하는 편보다 낫다.
 *
 * ## 순서를 새치기하지 않는다
 *
 * [AdmitSlotUseCase]는 **호출자를 승격시키지 않는다.** 언제나 대기열 맨 앞부터 뽑는다.
 * 그래서 내가 밀어 준 자리에 내가 아닌 앞사람이 들어가고, 나는 내가 맨 앞이 됐을 때
 * 누군가의 폴링에 의해 뽑힌다. 폴링이 빠른 사용자가 먼저 입장하는 일은 생기지 않는다.
 *
 * 승격을 상태 판정보다 **먼저** 하는 것도 이래서다. 이번 호출로 내가 뽑혔다면 같은 응답에
 * 곧바로 ADMITTED가 실려 나가고, 폴링 한 번을 더 기다리지 않는다.
 */
@UseCase
class InquiryWaitingTicketService(
    private val findTicketResult: FindTicketResult,
    private val isTicketAdmitted: IsTicketAdmitted,
    private val findWaitingTicketPosition: FindWaitingTicketPosition,
    private val admitSlot: AdmitSlotUseCase,
) : InquiryWaitingTicketUseCase {
    private fun waitingStatus(
        slot: WaitingQueueSlot,
        ticketId: String,
    ): Pair<QueueStatus, Long?> =
        findWaitingTicketPosition
            .query(WaitingTicketInquiry(slot = slot, ticketId = ticketId))
            ?.let { WAITING to it }
            ?: (EXPIRED to null)

    private fun admittedStatus(
        slot: WaitingQueueSlot,
        ticketId: String,
    ): Pair<QueueStatus, Long?>? =
        (ADMITTED to null)
            .takeIf { isTicketAdmitted.query(AdmissionInquiry(slot = slot, ticketId = ticketId)) }

    private fun status(
        slot: WaitingQueueSlot,
        ticketId: String,
    ): Pair<QueueStatus, Long?> =
        findTicketResult.query(ticketId)?.let { it to null }
            ?: admittedStatus(slot, ticketId)
            ?: waitingStatus(slot, ticketId)

    override fun execute(query: InquiryWaitingTicketQuery): InquiryWaitingTicketQueryResult {
        val slot =
            WaitingQueueSlot(
                restaurantId = query.restaurantId,
                date = query.date,
                startTime = query.startTime,
            )

        admitSlot.execute(slot)

        val (status, position) = status(slot, query.ticketId)

        return InquiryWaitingTicketQueryResult(
            ticketId = query.ticketId,
            status = status,
            position = position,
        )
    }
}
