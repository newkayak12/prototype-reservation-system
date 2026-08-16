package com.reservation.command.core.timetable.event

import com.reservation.command.core.support.DomainEvent
import java.time.Instant

data class SlotUnblocked(
    val slotId: String,
    val unblockedBy: String,
    val unblockedAt: Instant,
) : DomainEvent
