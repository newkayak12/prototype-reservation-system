package com.reservation.rest.queue.request

import com.reservation.queue.port.input.query.request.InquiryWaitingTicketQuery
import java.time.LocalDate
import java.time.LocalTime

data class InquiryWaitingTicketRequest(
    val date: LocalDate,
    val startTime: LocalTime,
) {
    fun toQuery(
        restaurantId: String,
        ticketId: String,
    ): InquiryWaitingTicketQuery =
        InquiryWaitingTicketQuery(
            restaurantId = restaurantId,
            date = date,
            startTime = startTime,
            ticketId = ticketId,
        )
}
