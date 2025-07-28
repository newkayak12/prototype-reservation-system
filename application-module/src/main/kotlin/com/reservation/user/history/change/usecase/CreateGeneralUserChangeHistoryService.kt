package com.reservation.user.history.change.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.user.history.change.port.input.CreateGeneralUserChangeHistoryUseCase
import com.reservation.user.history.change.port.input.command.request.CreateGeneralUserChangeHistoryCommand
import com.reservation.user.history.change.port.output.CreateGeneralUserChangeHistory
import org.springframework.transaction.annotation.Transactional

@UseCase
class CreateGeneralUserChangeHistoryService(
    val createGeneralUserChangeHistory: CreateGeneralUserChangeHistory,
) : CreateGeneralUserChangeHistoryUseCase {
    @Transactional
    override fun execute(command: CreateGeneralUserChangeHistoryCommand) {
        createGeneralUserChangeHistory.save(command.toInquiry())
    }
}
