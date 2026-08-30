package com.reservation.queue.port.input

import com.reservation.queue.port.input.command.request.EnterWaitingQueueCommand
import com.reservation.queue.port.input.command.response.EnterWaitingQueueCommandResult

interface EnterWaitingQueueUseCase {
    fun execute(command: EnterWaitingQueueCommand): EnterWaitingQueueCommandResult
}
