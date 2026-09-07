package com.reservation.rest.queue.response

import com.reservation.enumeration.QueueStatus
import com.reservation.queue.port.input.query.response.InquiryWaitingTicketQueryResult

data class InquiryWaitingTicketResponse(
    val ticketId: String,
    val status: QueueStatus,
    val position: Long? = null,
) {
    companion object {
        fun from(result: InquiryWaitingTicketQueryResult): InquiryWaitingTicketResponse =
            InquiryWaitingTicketResponse(
                ticketId = result.ticketId,
                status = result.status,
                position = result.position,
            )
    }
}
