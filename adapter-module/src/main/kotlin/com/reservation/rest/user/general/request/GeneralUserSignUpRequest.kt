package com.reservation.rest.user.general.request

import com.reservation.user.self.port.input.CreateGeneralUserCommand.CreateGeneralUserCommandDto
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotEmpty

data class GeneralUserSignUpRequest(
    @field:NotEmpty
    val loginId: String,
    @field:NotEmpty
    val password: String,
    @field:NotEmpty
    @field:Email
    val email: String,
    @field:NotEmpty
    val mobile: String,
    @field:NotEmpty
    val nickname: String,
) {
    fun toCommand(): CreateGeneralUserCommandDto =
        CreateGeneralUserCommandDto(
            loginId = loginId,
            password = password,
            email = email,
            mobile = mobile,
            nickname = nickname,
        )
}
