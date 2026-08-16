package com.reservation.command.core.timetable.event

import com.reservation.command.core.support.DomainEvent
import java.time.Instant

data class SlotBlocked(
    val slotId: String,
    val blockedBy: String,
    val blockedAt: Instant,
) : DomainEvent
