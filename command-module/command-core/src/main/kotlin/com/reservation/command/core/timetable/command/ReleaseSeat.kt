package com.reservation.command.core.timetable.command

import com.reservation.command.core.support.Command
import java.time.Instant

data class ReleaseSeat(
    val reservationId: String,
    val releasedAt: Instant,
) : Command
