package com.reservation.rest.user.seller.request

import com.reservation.authenticate.port.input.AuthenticateSellerUserQuery.SellerUserQueryDto
import jakarta.validation.constraints.NotBlank

data class SellerUserLoginRequest(
    @field:NotBlank
    val loginId: String,
    @field:NotBlank
    val password: String,
) {
    fun toQuery(): SellerUserQueryDto {
        return SellerUserQueryDto(loginId, password)
    }
}
