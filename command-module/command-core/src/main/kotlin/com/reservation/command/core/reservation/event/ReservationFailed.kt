package com.reservation.command.core.reservation.event

import com.reservation.command.core.support.DomainEvent
import java.time.LocalDateTime

data class ReservationFailed(
    val reservationId: String,
    val failedAt: LocalDateTime,
) : DomainEvent
