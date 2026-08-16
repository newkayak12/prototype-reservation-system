package com.reservation.command.core.reservation.command

import com.reservation.command.core.support.Command
import java.time.LocalDateTime

data class FailReservation(
    val failedAt: LocalDateTime,
) : Command
