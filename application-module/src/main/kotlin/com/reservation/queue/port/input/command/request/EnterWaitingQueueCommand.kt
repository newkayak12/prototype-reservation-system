package com.reservation.queue.port.input.command.request

import java.time.LocalDate
import java.time.LocalTime

data class EnterWaitingQueueCommand(
    val userId: String,
    val restaurantId: String,
    val date: LocalDate,
    val startTime: LocalTime,
)
