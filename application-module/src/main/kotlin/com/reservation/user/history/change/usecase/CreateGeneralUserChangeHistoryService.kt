package com.reservation.user.history.change.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.user.history.change.port.input.CreateGeneralUserChangeHistoryCommand
import com.reservation.user.history.change.port.input.CreateGeneralUserChangeHistoryCommand.CreateGeneralUserChangeHistoryCommandDto
import com.reservation.user.history.change.port.output.CreateGeneralUserChangeHistory
import org.springframework.transaction.annotation.Transactional

@UseCase
class CreateGeneralUserChangeHistoryService(
    val createGeneralUserChangeHistory: CreateGeneralUserChangeHistory,
) : CreateGeneralUserChangeHistoryCommand {
    @Transactional
    override fun execute(command: CreateGeneralUserChangeHistoryCommandDto) {
        createGeneralUserChangeHistory.save(command.toInquiry())
    }
}
