package com.reservation.queue.port.input

import com.reservation.queue.port.input.query.request.InquiryWaitingTicketQuery
import com.reservation.queue.port.input.query.response.InquiryWaitingTicketQueryResult

interface InquiryWaitingTicketUseCase {
    fun execute(query: InquiryWaitingTicketQuery): InquiryWaitingTicketQueryResult
}
