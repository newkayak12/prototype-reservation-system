package com.reservation.menu.port.input

import com.reservation.menu.port.input.response.FindMenusQueryResult

interface FindMenusUseCase {
    fun execute(id: String): List<FindMenusQueryResult>
}
