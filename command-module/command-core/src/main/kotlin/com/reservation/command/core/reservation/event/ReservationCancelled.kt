package com.reservation.command.core.reservation.event

import com.reservation.command.core.reservation.CancelActor
import com.reservation.command.core.support.DomainEvent
import java.time.LocalDateTime

data class ReservationCancelled(
    val reservationId: String,
    val cancelledBy: CancelActor,
    val reason: String?,
    val cancelledAt: LocalDateTime,
) : DomainEvent
