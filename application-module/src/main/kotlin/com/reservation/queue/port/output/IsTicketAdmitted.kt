package com.reservation.queue.port.output

import com.reservation.queue.vo.WaitingQueueSlot

/**
 * `ADMITTED:{key}` SET에 티켓이 들어 있는지 확인한다.
 * 대기열 폴링과 예약 엔드포인트 강제 게이트가 모두 이 포트를 사용한다.
 */
interface IsTicketAdmitted {
    fun query(inquiry: AdmissionInquiry): Boolean

    data class AdmissionInquiry(
        val slot: WaitingQueueSlot,
        val ticketId: String,
    )
}
