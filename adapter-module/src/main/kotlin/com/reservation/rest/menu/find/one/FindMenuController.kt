package com.reservation.rest.menu.find.one

import com.reservation.menu.port.input.FindMenuUseCase
import com.reservation.rest.menu.MenuUrl
import com.reservation.rest.menu.response.FindMenuResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class FindMenuController(
    private val findMenuUseCase: FindMenuUseCase,
) {
    @GetMapping(MenuUrl.FIND_MENU)
    fun findMenu(
        @PathVariable id: String,
    ): FindMenuResponse = FindMenuResponse.from(findMenuUseCase.execute(id))
}
