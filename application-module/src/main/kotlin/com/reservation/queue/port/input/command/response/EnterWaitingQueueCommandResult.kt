package com.reservation.queue.port.input.command.response

data class EnterWaitingQueueCommandResult(
    val ticketId: String,
    val position: Long,
)
