package com.reservation.command.core.reservation.event

import com.reservation.command.core.support.DomainEvent
import java.time.LocalDateTime

data class ReservationExpired(
    val reservationId: String,
    val expiredAt: LocalDateTime,
) : DomainEvent
