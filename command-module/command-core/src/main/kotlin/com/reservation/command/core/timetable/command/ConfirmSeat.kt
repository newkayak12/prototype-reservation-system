package com.reservation.command.core.timetable.command

import com.reservation.command.core.support.Command
import java.time.Instant

data class ConfirmSeat(
    val reservationId: String,
    val confirmedAt: Instant,
) : Command
