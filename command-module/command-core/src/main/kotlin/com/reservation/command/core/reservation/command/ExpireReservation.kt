package com.reservation.command.core.reservation.command

import com.reservation.command.core.support.Command
import java.time.LocalDateTime

data class ExpireReservation(
    val expiredAt: LocalDateTime,
) : Command
