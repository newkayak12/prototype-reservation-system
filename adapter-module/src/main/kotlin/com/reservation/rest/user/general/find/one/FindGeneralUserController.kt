package com.reservation.rest.user.general.find.one

import com.reservation.rest.user.general.GeneralUserUrl
import com.reservation.rest.user.general.response.FindGeneralUserResponse
import com.reservation.user.self.port.input.FindGeneralUserUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class FindGeneralUserController(
    val findGeneralUserUseCase: FindGeneralUserUseCase,
) {
    @GetMapping(GeneralUserUrl.FIND_USER)
    fun findGeneralUser(
        @PathVariable id: String,
    ): FindGeneralUserResponse = FindGeneralUserResponse.from(findGeneralUserUseCase.execute(id))
}
