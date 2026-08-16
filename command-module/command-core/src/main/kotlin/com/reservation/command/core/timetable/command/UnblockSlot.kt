package com.reservation.command.core.timetable.command

import com.reservation.command.core.support.Command
import java.time.Instant

data class UnblockSlot(
    val unblockedBy: String,
    val unblockedAt: Instant,
) : Command
