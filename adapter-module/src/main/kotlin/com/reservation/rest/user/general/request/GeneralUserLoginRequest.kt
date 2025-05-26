package com.reservation.rest.user.general.request

import com.reservation.user.self.port.input.AuthenticateGeneralUserQuery.GeneralUserQueryDto
import org.jetbrains.annotations.NotNull

data class GeneralUserLoginRequest(
    @NotNull
    val loginId: String,
    @NotNull
    val password: String,
) {
    fun toQuery(): GeneralUserQueryDto {
        return GeneralUserQueryDto(loginId, password)
    }
}
