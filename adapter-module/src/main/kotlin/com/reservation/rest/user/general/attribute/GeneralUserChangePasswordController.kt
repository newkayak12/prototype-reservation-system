package com.reservation.rest.user.general.attribute

import com.reservation.rest.common.response.BooleanResponse
import com.reservation.rest.user.general.GeneralUserUrl
import com.reservation.rest.user.general.request.GeneralUserChangePasswordRequest
import com.reservation.user.self.port.input.ChangeGeneralUserPasswordCommand
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class GeneralUserChangePasswordController(
    private val changeGeneralUserPasswordCommand: ChangeGeneralUserPasswordCommand,
) {
    @PatchMapping(GeneralUserUrl.CHANGE_PASSWORD)
    fun changePassword(
        @PathVariable id: String,
        @RequestBody @Valid request: GeneralUserChangePasswordRequest,
    ): BooleanResponse {
        return BooleanResponse.ok(changeGeneralUserPasswordCommand.execute(request.toCommand(id)))
    }
}
