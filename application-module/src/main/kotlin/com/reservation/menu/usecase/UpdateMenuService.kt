package com.reservation.menu.usecase

import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.config.annotations.UseCase
import com.reservation.menu.port.input.UpdateMenuUseCase
import com.reservation.menu.port.input.request.UpdateMenuCommand
import com.reservation.menu.port.output.LoadMenuById
import org.springframework.transaction.annotation.Transactional

@UseCase
class UpdateMenuService(
    private val loadMenuById: LoadMenuById,
) : UpdateMenuUseCase {
    @Transactional
    override fun execute(command: UpdateMenuCommand): Boolean {
        val menu = loadMenuById.loadById(command.id) ?: throw NoSuchPersistedElementException()
    }
}
