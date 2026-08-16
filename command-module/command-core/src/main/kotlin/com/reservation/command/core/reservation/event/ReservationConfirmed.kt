package com.reservation.command.core.reservation.event

import com.reservation.command.core.support.DomainEvent
import java.time.LocalDateTime

data class ReservationConfirmed(
    val reservationId: String,
    val confirmedAt: LocalDateTime,
) : DomainEvent
