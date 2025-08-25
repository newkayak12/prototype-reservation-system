package com.reservation.menu.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.menu.port.input.FindMenusUseCase
import com.reservation.menu.port.input.response.FindMenusQueryResult
import com.reservation.menu.port.output.FindMenus
import org.springframework.transaction.annotation.Transactional

@UseCase
class FindMenusService(
    private val findMenus: FindMenus,
) : FindMenusUseCase {
    @Transactional(readOnly = true)
    override fun execute(id: String): List<FindMenusQueryResult> {
        return findMenus.query(id).map { it.toQuery() }
    }
}
