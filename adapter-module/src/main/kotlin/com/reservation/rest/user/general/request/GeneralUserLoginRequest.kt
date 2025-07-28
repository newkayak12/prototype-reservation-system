package com.reservation.rest.user.general.request

import com.reservation.authenticate.port.input.query.request.GeneralUserQuery
import jakarta.validation.constraints.NotBlank

data class GeneralUserLoginRequest(
    @field:NotBlank
    val loginId: String,
    @field:NotBlank
    val password: String,
) {
    fun toQuery(): GeneralUserQuery {
        return GeneralUserQuery(loginId, password)
    }
}
