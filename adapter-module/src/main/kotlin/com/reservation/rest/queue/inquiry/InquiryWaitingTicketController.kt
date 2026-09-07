package com.reservation.rest.queue.inquiry

import com.reservation.queue.port.input.InquiryWaitingTicketUseCase
import com.reservation.rest.queue.WaitingQueueUrl
import com.reservation.rest.queue.request.InquiryWaitingTicketRequest
import com.reservation.rest.queue.response.InquiryWaitingTicketResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class InquiryWaitingTicketController(
    private val inquiryWaitingTicketUseCase: InquiryWaitingTicketUseCase,
) {
    @GetMapping(WaitingQueueUrl.QUEUE_TICKET)
    fun inquiryWaitingTicket(
        @PathVariable("restaurantId") restaurantId: String,
        @PathVariable("ticketId") ticketId: String,
        request: InquiryWaitingTicketRequest,
    ): InquiryWaitingTicketResponse =
        InquiryWaitingTicketResponse.from(
            inquiryWaitingTicketUseCase.execute(request.toQuery(restaurantId, ticketId)),
        )
}
