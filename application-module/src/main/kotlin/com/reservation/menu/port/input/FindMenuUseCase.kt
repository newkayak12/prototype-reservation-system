package com.reservation.menu.port.input

import com.reservation.menu.port.input.response.FindMenuQueryResult

interface FindMenuUseCase {
    fun execute(id: String): FindMenuQueryResult
}
