package com.reservation.rest.user.general.sign.find.password

import com.reservation.rest.common.response.BooleanResponse
import com.reservation.rest.user.general.GeneralUserUrl
import com.reservation.rest.user.general.request.FindGeneralUserPasswordRequest
import com.reservation.user.self.port.input.FindGeneralUserPasswordCommand
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class FindGeneralUserPasswordController(
    val findGeneralUserPasswordCommand: FindGeneralUserPasswordCommand,
) {
    @PatchMapping(GeneralUserUrl.FIND_LOST_PASSWORD)
    fun findLostPassword(
        @RequestBody request: FindGeneralUserPasswordRequest,
    ): BooleanResponse =
        BooleanResponse.ok(findGeneralUserPasswordCommand.execute(request.toCommand()))
}
