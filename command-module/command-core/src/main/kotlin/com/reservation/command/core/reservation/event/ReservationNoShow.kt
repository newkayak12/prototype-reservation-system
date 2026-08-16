package com.reservation.command.core.reservation.event

import com.reservation.command.core.support.DomainEvent
import java.time.LocalDateTime

data class ReservationNoShow(
    val reservationId: String,
    val judgedAt: LocalDateTime,
) : DomainEvent
