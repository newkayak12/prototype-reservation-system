package com.reservation.command.core.reservation.event

import com.reservation.command.core.support.DomainEvent
import java.time.LocalDateTime

data class RefundRequired(
    val reservationId: String,
    val rejectedAt: LocalDateTime,
) : DomainEvent
