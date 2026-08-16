package com.reservation.command.core.timetable.command

import com.reservation.command.core.support.Command
import java.time.Instant

data class HoldSeat(
    val reservationId: String,
    val userId: String,
    val heldAt: Instant,
    val holdExpiresAt: Instant,
) : Command
