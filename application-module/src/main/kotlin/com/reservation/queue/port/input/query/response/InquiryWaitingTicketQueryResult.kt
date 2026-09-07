package com.reservation.queue.port.input.query.response

import com.reservation.enumeration.QueueStatus

data class InquiryWaitingTicketQueryResult(
    val ticketId: String,
    val status: QueueStatus,
    val position: Long? = null,
)
