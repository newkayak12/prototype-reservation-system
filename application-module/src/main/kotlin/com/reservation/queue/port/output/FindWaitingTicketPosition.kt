package com.reservation.queue.port.output

import com.reservation.queue.vo.WaitingQueueSlot

/**
 * 대기열(ZSET) 안에서의 1-base 순번을 조회한다. 대기열에 없으면 null.
 */
interface FindWaitingTicketPosition {
    fun query(inquiry: WaitingTicketInquiry): Long?

    data class WaitingTicketInquiry(
        val slot: WaitingQueueSlot,
        val ticketId: String,
    )
}
