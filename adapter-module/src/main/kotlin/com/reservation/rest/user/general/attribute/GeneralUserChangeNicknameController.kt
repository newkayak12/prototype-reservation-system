package com.reservation.rest.user.general.attribute

import com.reservation.rest.common.response.BooleanResponse
import com.reservation.rest.user.general.GeneralUserUrl
import com.reservation.rest.user.general.request.GeneralUserChangeNicknameRequest
import com.reservation.user.self.port.input.ChangeGeneralUserNicknameCommand
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class GeneralUserChangeNicknameController(
    val changeGeneralUserNicknameCommand: ChangeGeneralUserNicknameCommand,
) {
    @PatchMapping(GeneralUserUrl.CHANGE_NICKNAME)
    fun changeNickname(
        @PathVariable id: String,
        @RequestBody request: GeneralUserChangeNicknameRequest,
    ): BooleanResponse =
        BooleanResponse.resetContents(
            changeGeneralUserNicknameCommand.execute(request.toCommand(id)),
        )
}
