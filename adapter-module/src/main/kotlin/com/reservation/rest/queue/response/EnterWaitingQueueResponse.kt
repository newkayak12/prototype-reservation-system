package com.reservation.rest.queue.response

import com.reservation.queue.port.input.command.response.EnterWaitingQueueCommandResult

data class EnterWaitingQueueResponse(
    val ticketId: String,
    val position: Long,
) {
    companion object {
        fun from(result: EnterWaitingQueueCommandResult): EnterWaitingQueueResponse =
            EnterWaitingQueueResponse(
                ticketId = result.ticketId,
                position = result.position,
            )
    }
}
