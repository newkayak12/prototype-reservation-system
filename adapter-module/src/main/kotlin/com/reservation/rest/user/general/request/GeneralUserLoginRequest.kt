package com.reservation.rest.user.general.request

import com.reservation.authenticate.port.input.AuthenticateGeneralUserQuery.GeneralUserQueryDto
import jakarta.validation.constraints.NotBlank

data class GeneralUserLoginRequest(
    @field:NotBlank
    val loginId: String,
    @field:NotBlank
    val password: String,
) {
    fun toQuery(): GeneralUserQueryDto {
        return GeneralUserQueryDto(loginId, password)
    }
}
