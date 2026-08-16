package com.reservation.command.core.reservation.command

import com.reservation.command.core.reservation.CancelActor
import com.reservation.command.core.support.Command
import java.time.LocalDateTime

data class CancelReservation(
    val cancelledBy: CancelActor,
    val requesterId: String,
    val reason: String?,
    val cancelledAt: LocalDateTime,
) : Command
