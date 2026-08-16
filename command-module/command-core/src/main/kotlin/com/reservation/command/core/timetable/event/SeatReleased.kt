package com.reservation.command.core.timetable.event

import com.reservation.command.core.support.DomainEvent
import java.time.Instant

data class SeatReleased(
    val slotId: String,
    val reservationId: String,
    val releasedAt: Instant,
) : DomainEvent
