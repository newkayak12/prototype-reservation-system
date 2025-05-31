package com.reservation.rest.user.general.request

import com.reservation.user.self.port.input.AuthenticateGeneralUserQuery.GeneralUserQueryDto
import jakarta.validation.constraints.NotEmpty

data class GeneralUserLoginRequest(
    @field:NotEmpty
    val loginId: String,
    @field:NotEmpty
    val password: String,
) {
    fun toQuery(): GeneralUserQueryDto {
        return GeneralUserQueryDto(loginId, password)
    }
}
