package com.reservation.command.core.timetable.command

import com.reservation.command.core.support.Command
import java.time.Instant

data class BlockSlot(
    val blockedBy: String,
    val blockedAt: Instant,
) : Command
