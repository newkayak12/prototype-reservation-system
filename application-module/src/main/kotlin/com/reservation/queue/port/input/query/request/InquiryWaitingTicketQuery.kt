package com.reservation.queue.port.input.query.request

import java.time.LocalDate
import java.time.LocalTime

data class InquiryWaitingTicketQuery(
    val restaurantId: String,
    val date: LocalDate,
    val startTime: LocalTime,
    val ticketId: String,
)
