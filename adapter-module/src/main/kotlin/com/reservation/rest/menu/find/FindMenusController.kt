package com.reservation.rest.menu.find

import com.reservation.menu.port.input.FindMenusUseCase
import com.reservation.rest.common.response.ListResponse
import com.reservation.rest.menu.MenuUrl
import com.reservation.rest.menu.response.FindMenuResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class FindMenusController(
    private val findMenusUseCase: FindMenusUseCase,
) {
    @GetMapping(MenuUrl.FIND_MENUS)
    fun findMenus(
        @PathVariable("id") id: String,
    ): ListResponse<FindMenuResponse> =
        ListResponse.ok(
            findMenusUseCase.execute(id).map {
                FindMenuResponse.from(it)
            },
        )
}
