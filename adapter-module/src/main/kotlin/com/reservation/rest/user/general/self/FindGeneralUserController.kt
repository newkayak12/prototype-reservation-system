package com.reservation.rest.user.general.self

import com.reservation.rest.user.general.GeneralUserUrl
import com.reservation.rest.user.general.response.FindGeneralUserResponse
import com.reservation.user.self.port.input.FindGeneralUserQuery
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class FindGeneralUserController(
    val findGeneralUserQuery: FindGeneralUserQuery,
) {
    @GetMapping(GeneralUserUrl.FIND_USER)
    fun findGeneralUser(
        @PathVariable id: String,
    ): FindGeneralUserResponse = FindGeneralUserResponse.from(findGeneralUserQuery.execute(id))
}
