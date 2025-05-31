package com.reservation.user.history.access.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.user.history.access.port.input.CreateUserAccessHistoriesCommand
import com.reservation.user.history.access.port.input.CreateUserAccessHistoriesCommand.CreateUserHistoryCommandDto
import com.reservation.user.history.access.port.output.CreateUserAccessHistories
import org.springframework.transaction.annotation.Transactional

@UseCase
class CreateUserAccessHistoriesUseCase(
    val createUserAccessHistories: CreateUserAccessHistories,
) : CreateUserAccessHistoriesCommand {
    @Transactional
    override fun execute(histories: List<CreateUserHistoryCommandDto>) {
        histories.run {
            if (isEmpty()) {
                return@run
            }

            createUserAccessHistories.saveAll(map { it.toDto() }.toList())
        }
    }
}
