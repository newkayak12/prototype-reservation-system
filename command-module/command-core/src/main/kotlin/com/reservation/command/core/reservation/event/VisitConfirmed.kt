package com.reservation.command.core.reservation.event

import com.reservation.command.core.reservation.VisitConfirmer
import com.reservation.command.core.support.DomainEvent
import java.time.LocalDateTime

data class VisitConfirmed(
    val reservationId: String,
    val confirmedBy: VisitConfirmer,
    val confirmedAt: LocalDateTime,
) : DomainEvent
