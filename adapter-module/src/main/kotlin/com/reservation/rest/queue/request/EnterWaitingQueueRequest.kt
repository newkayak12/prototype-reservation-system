package com.reservation.rest.queue.request

import com.reservation.queue.port.input.command.request.EnterWaitingQueueCommand
import java.time.LocalDate
import java.time.LocalTime

data class EnterWaitingQueueRequest(
    val date: LocalDate,
    val startTime: LocalTime,
) {
    fun toCommand(
        userId: String,
        restaurantId: String,
    ): EnterWaitingQueueCommand =
        EnterWaitingQueueCommand(
            userId = userId,
            restaurantId = restaurantId,
            date = date,
            startTime = startTime,
        )
}
