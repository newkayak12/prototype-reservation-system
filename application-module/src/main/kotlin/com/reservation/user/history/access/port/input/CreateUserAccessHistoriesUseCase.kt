package com.reservation.user.history.access.port.input

import com.reservation.user.history.access.port.input.command.request.CreateUserHistoryCommand

interface CreateUserAccessHistoriesUseCase {
    fun execute(histories: List<CreateUserHistoryCommand>)
}
