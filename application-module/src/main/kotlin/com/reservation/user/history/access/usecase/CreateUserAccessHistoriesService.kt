package com.reservation.user.history.access.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.user.history.access.port.input.CreateUserAccessHistoriesUseCase
import com.reservation.user.history.access.port.input.command.request.CreateUserHistoryCommand
import com.reservation.user.history.access.port.output.CreateUserAccessHistories
import org.springframework.transaction.annotation.Transactional

@UseCase
class CreateUserAccessHistoriesService(
    val createUserAccessHistories: CreateUserAccessHistories,
) : CreateUserAccessHistoriesUseCase {
    @Transactional
    override fun execute(histories: List<CreateUserHistoryCommand>) {
        histories.run {
            if (isEmpty()) {
                return@run
            }

            createUserAccessHistories.saveAll(map { it.toDto() }.toList())
        }
    }
}
