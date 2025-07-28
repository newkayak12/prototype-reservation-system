package com.reservation.user.history.change.port.input

import com.reservation.user.history.change.port.input.command.request.CreateGeneralUserChangeHistoryCommand

interface CreateGeneralUserChangeHistoryUseCase {
    fun execute(command: CreateGeneralUserChangeHistoryCommand)
}
