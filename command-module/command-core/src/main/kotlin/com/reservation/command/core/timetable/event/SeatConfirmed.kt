package com.reservation.command.core.timetable.event

import com.reservation.command.core.support.DomainEvent
import java.time.Instant

data class SeatConfirmed(
    val slotId: String,
    val reservationId: String,
    val confirmedAt: Instant,
) : DomainEvent
